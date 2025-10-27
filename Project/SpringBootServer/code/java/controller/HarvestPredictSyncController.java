package com.smartfarm.smartfarm_server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/gemini/harvest")
public class HarvestPredictSyncController {

    private static final String GEMINI_MODEL = "gemini-2.5-flash"; // ✅ 모델명 상수화
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Storage storage;
    private final PhotoRepository photoRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${app.gcs.default-bucket:}")
    private String defaultBucket;

    public HarvestPredictSyncController(PhotoRepository photoRepository) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.photoRepository = photoRepository;
    }

    @PostMapping(
            value = "/predict",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> predict(
            @RequestParam("mode") String mode,        // upload | gcs
            @RequestParam("cropName") String cropName,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gcsUri", required = false) String gcsUri
    ) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", false);

        // ── 1️⃣ 입력 검증 ────────────────────────────────────────────────
        if (!StringUtils.hasText(apiKey))
            return bad(resp, "apiKey", "gemini.api.key 미설정", "application.properties에 키를 설정하세요.");
        if (!StringUtils.hasText(cropName))
            return bad(resp, "cropName", "작물 이름이 비어 있음", "작물 이름을 입력하세요.");
        if (!"upload".equalsIgnoreCase(mode) && !"gcs".equalsIgnoreCase(mode))
            return bad(resp, "mode", "잘못된 모드", "upload 또는 gcs 중 하나를 선택하세요.");

        boolean isUpload = "upload".equalsIgnoreCase(mode);
        boolean hasFile = (image != null && !image.isEmpty());
        boolean hasGcs = StringUtils.hasText(gcsUri);

        if (isUpload && !hasFile)
            return bad(resp, "image", "이미지 비어 있음", "파일을 업로드하세요.");

        // ✅ 파일명 일부 입력 시 자동 보정
        if (!isUpload) {
            if (!hasGcs)
                return bad(resp, "gcsUri", "GCS 경로 비어 있음", "파일명을 입력하세요.");

            if (!gcsUri.trim().startsWith("gs://")) {
                String prefix = StringUtils.hasText(defaultBucket)
                        ? "gs://" + defaultBucket + "/farm01_cam01_"
                        : "gs://datafarm-picture/farm01_cam01_";
                gcsUri = prefix + gcsUri.trim();
                resp.put("autoCompletedUri", gcsUri);
            }
        }

        try {
            // ── 2️⃣ 이미지 로딩 ────────────────────────────────────────────
            byte[] imageBytes;
            String mimeType;
            String fileName;

            if (isUpload) {
                imageBytes = image.getBytes();
                if (imageBytes.length > DataSize.ofMegabytes(5).toBytes()) {
                    return bad(resp, "image", "이미지 용량 초과", "5MB 이하 파일만 허용됩니다.");
                }

                fileName = image.getOriginalFilename();
                mimeType = Optional.ofNullable(image.getContentType())
                        .orElseGet(() -> {
                            try {
                                return Files.probeContentType(Paths.get(fileName));
                            } catch (Exception e) {
                                return "image/jpeg";
                            }
                        });
            } else {
                GcsPath path = parseGcsUri(gcsUri);
                String bucket = StringUtils.hasText(path.bucket) ? path.bucket : defaultBucket;
                if (!StringUtils.hasText(bucket))
                    throw new IllegalArgumentException("GCS 버킷이 없습니다.");

                Blob blob = storage.get(BlobId.of(bucket, path.object));
                if (blob == null || !blob.exists())
                    throw new IllegalArgumentException("GCS 객체를 찾을 수 없습니다: " + gcsUri);

                imageBytes = blob.getContent();
                mimeType = blob.getContentType() != null ? blob.getContentType() : "image/jpeg";
                fileName = path.object;
            }

            // ── 3️⃣ brightnessRatio 가져오기 ────────────────────────────────
            Float brightnessRatio = null;
            if (!isUpload) {
                String keyword = gcsUri.substring(gcsUri.lastIndexOf('/') + 1);
                Optional<Photo> photoOpt = photoRepository.findTopByPhotoUrlContainingOrderByUploadDateDesc(keyword);
                if (photoOpt.isPresent()) {
                    brightnessRatio = photoOpt.get().getBrightnessRatio();
                    // DB 값이 0~1 사이면 100배 해줌
                    if (brightnessRatio != null && brightnessRatio < 1.0f) {
                        brightnessRatio *= 100;
                    }
                    resp.put("brightnessRatio", brightnessRatio);
                }
            }

            // ── 4️⃣ 컬러 분석 이미지 판별 ────────────────────────────────
            boolean isColorMask = isPseudoColorImage(imageBytes);

            // ✅ 프롬프트 생성
            String promptText = buildPrompt(isColorMask, cropName, brightnessRatio);

            // ── 5️⃣ Gemini API 요청 ─────────────────────────────────────────
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(
                                    Map.of("inline_data", Map.of(
                                            "mime_type", mimeType,
                                            "data", base64
                                    )),
                                    Map.of("text", promptText)
                            )
                    ))
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + GEMINI_MODEL + ":generateContent?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // ── 6️⃣ 응답 파싱 ─────────────────────────────────────────
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode node = objectMapper.readTree(response.getBody());
                String text = node.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText("결과를 불러오지 못했습니다.");

                resp.put("ok", true);
                resp.put("message", "✅ 예측 성공");
                resp.put("crop", cropName);
                resp.put("result", text);
                return ResponseEntity.ok(resp);
            } else {
                resp.put("status", response.getStatusCodeValue());
                resp.put("errorBody", response.getBody());
                return ResponseEntity.ok(resp);
            }

        } catch (Exception e) {
            resp.put("error", Map.of(
                    "message", e.getMessage(),
                    "exception", e.getClass().getSimpleName()
            ));
            return ResponseEntity.ok(resp);
        }
    }

    // ── Helper ──────────────────────────────────────────────
    private static ResponseEntity<Map<String, Object>> bad(Map<String, Object> resp, String field, String title, String detail) {
        resp.put("errors", List.of(Map.of("field", field, "title", title, "detail", detail)));
        return ResponseEntity.ok(resp);
    }

    private record GcsPath(String bucket, String object) {}
    private static GcsPath parseGcsUri(String uri) {
        if (!StringUtils.hasText(uri)) return new GcsPath(null, null);
        String u = uri.trim();
        if (u.startsWith("gs://")) {
            String rest = u.substring(5);
            int slash = rest.indexOf('/');
            if (slash < 0) return new GcsPath(rest, "");
            return new GcsPath(rest.substring(0, slash), rest.substring(slash + 1));
        } else {
            int slash = u.indexOf('/');
            if (slash < 0) return new GcsPath(u, "");
            return new GcsPath(u.substring(0, slash), u.substring(slash + 1));
        }
    }

    // 🎨 컬러 분석용 이미지 판별
    private boolean isPseudoColorImage(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) return false;

            int w = img.getWidth(), h = img.getHeight();
            long magentaCount = 0, blueCount = 0, total = (long) w * h;

            for (int y = 0; y < h; y += 4) {
                for (int x = 0; x < w; x += 4) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (r > 150 && b > 150 && g < 120) magentaCount++;
                    else if (b > 150 && r < 100 && g < 100) blueCount++;
                }
            }

            double magentaRatio = (double) magentaCount / total;
            double blueRatio = (double) blueCount / total;

            // ✅ 5% 이상일 때 색상 마스크로 인식 (완화됨)
            return magentaRatio > 0.05 && blueRatio > 0.05;
        } catch (Exception e) {
            return false;
        }
    }

    // 🌾 프롬프트 생성 로직
    private String buildPrompt(boolean isColorMask, String cropName, Float brightnessRatio) {
        StringBuilder prompt = new StringBuilder();
        if (isColorMask) {
            prompt.append("""
                    당신은 농작물 생육 분석 전문가예요.
                    이 이미지는 색상 기반 생육 분석용 영상입니다.
                    - 마젠타(보라/핑크)는 잎 영역, 파랑/검정은 배경입니다.
                    마젠타 영역의 면적, 색 농도, 형태를 기반으로
                    생장 단계(초기/중기/수확기)를 2~3문장으로 설명하고 예상 수확시기를 제시해주세요.
                    """);
        } else {
            prompt.append("""
                    당신은 농작물 전문가예요.
                    주어진 이미지와 작물 이름을 보고 현재 상태를 요약하고,
                    예상 수확 시기를 2~3문장으로 간단히 알려주세요.
                    """);
        }

        if (brightnessRatio != null) {
            prompt.append("\n추가 데이터: 이 이미지의 마젠타 영역(잎 면적 비율)은 전체 픽셀의 ")
                    .append(String.format("%.2f", brightnessRatio))
                    .append("% 입니다. 이 수치를 참고하여 생장 정도를 함께 고려하세요.");
        }

        prompt.append("\n작물 이름: ").append(cropName)
                .append("\n불확실하다면 '정확한 판단이 어렵습니다.'라고 답하세요.");
        return prompt.toString();
    }
}

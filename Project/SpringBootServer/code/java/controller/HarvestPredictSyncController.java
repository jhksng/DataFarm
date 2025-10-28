package com.smartfarm.smartfarm_server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import com.smartfarm.smartfarm_server.service.GrowthAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/gemini/harvest")
public class HarvestPredictSyncController {

    private static final Logger logger = LoggerFactory.getLogger(HarvestPredictSyncController.class);

    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Storage storage;
    private final PhotoRepository photoRepository;

    @Autowired
    private GrowthAnalysisService growthAnalysisService; // ✅ 추가됨

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

        // ── 1️⃣ 입력 검증
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

        // ── 2️⃣ GCS 경로 보정
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
            // ── 3️⃣ 이미지 로드
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
                        .orElse(Files.probeContentType(Paths.get(fileName)));
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

            // ── 4️⃣ brightnessRatio (gcs 모드)
            Float brightnessRatio = null;
            Double dayDeltaPct = null;

            if (!isUpload) {
                // ─ brightnessRatio 계산
                String keyword = gcsUri.substring(gcsUri.lastIndexOf('/') + 1);
                photoRepository.findTopByPhotoUrlContainingOrderByUploadDateDesc(keyword)
                        .ifPresent(photo -> {
                            Float ratio = normalizeBrightnessRatio(photo.getBrightnessRatio());
                            if (ratio != null) {
                                resp.put("brightnessRatio", ratio);
                            }
                        });

                // ─ GrowthAnalysisService로 성장률 계산
                Map<String, Double> growthStats = growthAnalysisService.calculateDailyGrowthChange(cropName);
                dayDeltaPct = growthStats.get("growthChangePercentage");

                if (dayDeltaPct != null) {
                    resp.put("growthChangePercentage", dayDeltaPct);
                    resp.put("previousAvg", growthStats.get("yesterdayAvg"));
                    resp.put("recentAvg", growthStats.get("todayAvg"));
                    resp.put("window", "last48_today_vs_yesterday");

                    logger.info(String.format(
                            "🌿 [%s] 성장률 계산 완료: 어제 %.2f%% → 오늘 %.2f%% (전일 대비 %.2f%%)",
                            cropName, growthStats.get("yesterdayAvg"),
                            growthStats.get("todayAvg"), dayDeltaPct
                    ));
                } else {
                    // ✅ GCS 모드지만 데이터 부족한 경우
                    logger.warn("⚠️ [{}] 성장률 계산 실패 (데이터 부족)", cropName);
                }

            } else {
                // ✅ 업로드 모드: 수치 데이터 완전히 제외
                brightnessRatio = null;
                dayDeltaPct = null;
            }

            // ── 6️⃣ 컬러 분석 이미지 판별
            boolean isColorMask = isPseudoColorImage(imageBytes);

            // ✅ AI 프롬프트 생성 (공통 계산 결과도 함께 전달)
            String promptText = buildPrompt(isColorMask, cropName, brightnessRatio, dayDeltaPct);

            // ── 7️⃣ Gemini API 호출
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

            // ── 8️⃣ 응답 처리
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

    private Float normalizeBrightnessRatio(Float ratio) {
        if (ratio == null) return null;
        if (ratio < 1.0f) return ratio * 100f; // 0~1 스케일 → %로 변환
        return ratio;
    }

    // ── Helper
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
            return magentaRatio > 0.05 && blueRatio > 0.05;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildPrompt(boolean isColorMask, String cropName, Float brightnessRatio, Double dayDeltaPct) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("작물 이름: ").append(cropName).append("\n");

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
            prompt.append("\n추가 데이터1: 현재 이미지의 잎 면적 비율(마젠타 비중)은 ")
                    .append(String.format(Locale.US, "%.2f", brightnessRatio))
                    .append("% 입니다.");
        }

        if (dayDeltaPct != null) {
            prompt.append("\n추가 데이터2: 최근 24장 평균 대비 전일 대비 성장률은 ")
                    .append(String.format(Locale.US, "%.2f", dayDeltaPct))
                    .append("% 입니다. (양수=증가, 음수=감소)");
        }

        prompt.append("""
    
            판단 규칙:
            - 변화율 ≤ -5%: '시들거나 잎 면적 감소 가능성 높음'으로 판단하고, 수분 부족·고온·병해·영양결핍 등의 원인을 우선 설명하세요.
            - -5% < 변화율 ≤ 0%: '성장 둔화 가능성'으로 판단하고, 온도·습도·광량 등 환경 요인을 점검하세요.
            - 0% < 변화율 < +5%: '유지 또는 완만한 성장'으로 보고, 정상적인 생육 상태로 간주하세요.
            - 변화율 ≥ +5%: '양호' 또는 '성장 증가'로 요약하세요.
            LED 조명으로 엽록소 색이 왜곡될 수 있으니, 잎 끝 말림·처짐·수분 스트레스 징후도 함께 고려해 주세요.
        """);

        return prompt.toString();
    }
}

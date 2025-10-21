package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.service.MqttPublisherService;
import com.smartfarm.smartfarm_server.service.PhotoService;
import com.smartfarm.smartfarm_server.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 💡 페이지네이션 관련 import 추가
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.net.URL;
import java.util.List; // List<Photo>는

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j // 👈 log 객체를 자동으로 생성해줍니다.
public class PhotoController {

    private final PhotoService photoService;
    private final MqttPublisherService mqttPublisherService;
    private final StorageService storageService;

    // 💡 페이지네이션을 적용한 Photo 목록 조회 엔드포인트로 수정
    @GetMapping("/photos")
    public ResponseEntity<Page<Photo>> getPhotosPaged(
            // 💡 size를 8로 변경하여 HTML의 pageSize와 일치시킵니다.
            @PageableDefault(size = 8, sort = "uploadDate", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("GET /api/photos (Paged) 요청이 들어왔습니다. 페이지 정보: {}", pageable);

        // PhotoService의 새 메서드를 호출하여 페이지 단위로 데이터를 가져옵니다.
        Page<Photo> photoPage = photoService.getPhotosPaged(pageable);

        log.info("DB에서 총 {}개 중 현재 페이지 {}/{}에 해당하는 {}개의 사진 목록을 조회했습니다.",
                photoPage.getTotalElements(),
                photoPage.getNumber() + 1,
                photoPage.getTotalPages(),
                photoPage.getContent().size());

        // Spring의 Page 객체는 사진 목록(content)과 페이지 메타 정보를 모두 담고 있습니다.
        return ResponseEntity.ok(photoPage);
    }

    // 사진 촬영 요청
    @PostMapping("/request-photo-capture")
    public ResponseEntity<String> requestPhotoCapture() {
        log.info("POST /api/request-photo-capture 요청이 들어왔습니다.");

        String topic = "farm/raspi-01/camera-command";
        String message = "capture";

        try {
            log.info("MQTT 토픽 '{}'에 메시지 '{}'를 발행합니다.", topic, message);
            mqttPublisherService.publish(topic, message);
            log.info("사진 촬영 요청 메시지 발행 성공.");
            return ResponseEntity.ok("사진 촬영 요청이 성공적으로 전송되었습니다.");
        } catch (Exception e) {
            log.error("사진 촬영 요청 메시지 발행 실패.", e);
            return ResponseEntity.status(500).body("사진 촬영 요청 메시지 발행 실패: " + e.getMessage());
        }
    }

    // 🔹 GCS 서명 URL 발급
    @GetMapping("/signed-url")
    public ResponseEntity<String> getSignedUrl(@RequestParam String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            log.error("Signed URL 요청 실패: filePath가 비어 있음");
            return ResponseEntity.badRequest().body("filePath 파라미터가 누락되었습니다.");
        }
        try {
            log.info("Generating signed URL for filePath: {}", filePath);
            URL signedUrl = storageService.generateSignedUrl(filePath);
            log.info("Signed URL generated successfully: {}", signedUrl);
            return ResponseEntity.ok(signedUrl.toString());
        } catch (IllegalArgumentException e) {
            log.error("잘못된 파일 경로: {}", filePath, e);
            return ResponseEntity.badRequest().body("잘못된 파일 경로입니다: " + e.getMessage());
        } catch (Exception e) { // IOException이 이미 Exception에 포함됩니다.
            log.error("서명 URL 생성 실패: {}", filePath, e);
            return ResponseEntity.status(500).body("서명 URL 생성 실패: " + e.getMessage());
        }
    }
}

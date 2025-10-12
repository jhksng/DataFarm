//package com.smartfarm.smartfarm_server.controller;
//
//import com.smartfarm.smartfarm_server.model.Photo;
//import com.smartfarm.smartfarm_server.service.MqttPublisherService;
//import com.smartfarm.smartfarm_server.service.PhotoService;
//import com.smartfarm.smartfarm_server.service.StorageService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j; // Slf4j 어노테이션을 사용하려면 이 import가 필요합니다.
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.net.URL;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api")
//@RequiredArgsConstructor
//@Slf4j // 👈 log 객체를 자동으로 생성해줍니다.
//public class PhotoController {
//
//    private final PhotoService photoService;
//    private final MqttPublisherService mqttPublisherService;
//    private final StorageService storageService;
//
//    // DB에서 Photo 목록 조회
//    @GetMapping("/photos")
//    public ResponseEntity<List<Photo>> getAllPhotos() {
//        log.info("GET /api/photos 요청이 들어왔습니다.");
//
//        List<Photo> photos = photoService.getAllPhotos();
//
//        // 조회된 사진 목록의 크기를 로그로 남겨 데이터 유무를 확인합니다.
//        log.info("DB에서 {}개의 사진 목록을 조회했습니다.", photos.size());
//
//        return ResponseEntity.ok(photos);
//    }
//
//    // 사진 촬영 요청
//    @PostMapping("/request-photo-capture")
//    public ResponseEntity<String> requestPhotoCapture() {
//        log.info("POST /api/request-photo-capture 요청이 들어왔습니다.");
//
//        String topic = "farm/raspi-01/camera-command";
//        String message = "capture";
//
//        try {
//            log.info("MQTT 토픽 '{}'에 메시지 '{}'를 발행합니다.", topic, message);
//            mqttPublisherService.publish(topic, message);
//            log.info("사진 촬영 요청 메시지 발행 성공.");
//            return ResponseEntity.ok("사진 촬영 요청이 성공적으로 전송되었습니다.");
//        } catch (Exception e) {
//            log.error("사진 촬영 요청 메시지 발행 실패.", e);
//            return ResponseEntity.status(500).body("사진 촬영 요청 메시지 발행 실패: " + e.getMessage());
//        }
//    }
//
//    // 🔹 GCS 서명 URL 발급
//    @GetMapping("/signed-url")
//    public ResponseEntity<String> getSignedUrl(@RequestParam String filePath) {
//        if (filePath == null || filePath.isEmpty()) {
//            log.error("Signed URL 요청 실패: filePath가 비어 있음");
//            return ResponseEntity.badRequest().body("filePath 파라미터가 누락되었습니다.");
//        }
//        try {
//            log.info("Generating signed URL for filePath: {}", filePath);
//            URL signedUrl = storageService.generateSignedUrl(filePath);
//            log.info("Signed URL generated successfully: {}", signedUrl);
//            return ResponseEntity.ok(signedUrl.toString());
//        } catch (IllegalArgumentException e) {
//            log.error("잘못된 파일 경로: {}", filePath, e);
//            return ResponseEntity.badRequest().body("잘못된 파일 경로입니다: " + e.getMessage());
//        } catch (Exception e) { // IOException이 이미 Exception에 포함됩니다.
//            log.error("서명 URL 생성 실패: {}", filePath, e);
//            return ResponseEntity.status(500).body("서명 URL 생성 실패: " + e.getMessage());
//        }
//    }
//}
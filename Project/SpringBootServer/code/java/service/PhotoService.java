package com.smartfarm.smartfarm_server.service;



import com.smartfarm.smartfarm_server.model.Photo;

import com.smartfarm.smartfarm_server.repository.PhotoRepository;

import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;

import com.smartfarm.smartfarm_server.repository.CropInfoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.util.Optional;



@Service

@RequiredArgsConstructor

public class PhotoService {



    private final PhotoRepository photoRepository;

    private final MqttPublisherService mqttPublisherService;

    private final ModuleStatusRepository moduleStatusRepository;

    private final CropInfoRepository cropInfoRepository;



// ✅ FarmControlScheduler 주입 유지

    private final FarmControlScheduler farmControlScheduler;



    @Transactional

    public void savePhoto(String photoUrl, String cropName) {

        Photo photo = new Photo(photoUrl, cropName);

        photoRepository.save(photo);

        System.out.println("📸 사진이 DB에 저장되었습니다: " + photoUrl + " (" + cropName + ")");

    }



    /**

     * MQTT 분석 결과 수신 시 brightnessRatio를 업데이트합니다.

     */

    @Transactional

    public void updatePhotoRatioByPhotoUrl(String photoUrl, Float ratio) {

        System.out.println("🟡 updatePhotoRatioByPhotoUrl 호출됨: url=" + photoUrl + ", ratio=" + ratio);



        Optional<Photo> photoOptional = photoRepository.findByPhotoUrl(photoUrl);



        if (photoOptional.isEmpty()) {

            String suffix = extractFileName(photoUrl);

            if (suffix != null) {

                photoOptional = photoRepository.findByPhotoUrlEndingWith(suffix);

            }

        }



        if (photoOptional.isPresent()) {

            Photo photo = photoOptional.get();

            photo.setBrightnessRatio(ratio);

            photoRepository.save(photo);

            System.out.println("✅ PhotoService: 밝기 비율 업데이트 성공 및 저장 완료 → " + photo.getPhotoUrl() + " | ratio=" + ratio);

        } else {

            System.out.println("❌ PhotoService: DB에서 사진 정보를 찾을 수 없어 밝기 비율 업데이트 실패.");

        }

    }



    private String extractFileName(String url) {

        if (url == null) return null;

        int idx = url.lastIndexOf('/');

        return (idx >= 0 && idx + 1 < url.length()) ? url.substring(idx + 1) : url;

    }



    @Transactional(readOnly = true)

    public Page<Photo> getPhotosPaged(Pageable pageable) {

        return photoRepository.findAll(pageable);

    }



    /**

     * [최종 수정] 1시간마다 자동 촬영 명령을 내립니다.

     * 라즈베리파이에서 LED ON/OFF를 관리하므로, 서버는 오직 '끄지 못하게 막는' 역할만 수행합니다.

     */
//
    @Scheduled(fixedRate = 3600000, initialDelay = 3600000) // 1시간 (3600000ms)

    @Transactional

    public void scheduledPhotoCapture() {

        final String DEVICE_ID = "raspi-01";

        final String CAMERA_TOPIC = "farm/" + DEVICE_ID + "/camera-command";

// LED가 켜지고 꺼지는 데 걸리는 시간을 충분히 포함하여 설정합니다.

        final int OVERRIDE_DURATION_MINUTES = 3; // 예를 들어 2분 설정



        System.out.println("📸 1시간 주기 자동 촬영 스케줄러 실행...");



// 1️⃣ LED 강제 ON 오버라이드 활성화 (핵심)

// 라즈베리파이의 쓰레드가 LED를 켜는 동안, 1분 주기 스케줄러가 '조도 초과'를 이유로 OFF 명령을 보내는 것을 방지합니다.

        farmControlScheduler.enablePhotoShootLedOn(OVERRIDE_DURATION_MINUTES);

        System.out.println("✅ LED 강제 ON 오버라이드 설정 (" + OVERRIDE_DURATION_MINUTES + "분).");



// 2️⃣ 사진 촬영 명령 전송

// 라즈베리파이는 이 명령을 받으면:

// 1. LED를 켜는 쓰레드를 시작하고 (LED ON)

// 2. 사진을 캡처한 후

// 3. LED를 끄는 쓰레드를 종료합니다 (LED OFF)

        String captureMessage = "capture";

        mqttPublisherService.publish(CAMERA_TOPIC, captureMessage);

        System.out.println("✅ 사진 촬영 명령 발행 → " + CAMERA_TOPIC);

    }

}
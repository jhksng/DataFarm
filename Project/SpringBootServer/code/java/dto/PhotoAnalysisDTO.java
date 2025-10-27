package com.smartfarm.smartfarm_server.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 라즈베리파이에서 MQTT로 전송되는 JSON 페이로드를 매핑하기 위한 DTO
 * 두 단계의 메시지를 모두 처리하기 위해 brightnessRatio는 Float 타입의 객체로 정의됩니다.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PhotoAnalysisDTO {

    // 사진 파일 경로 (필수)
    private String fileName;

    // 이미지 분석 결과 (밝기 비율). 분석 결과 전송 시에만 값이 존재 (선택적)
    // 값이 없을 때는 null이 되므로 Float 객체 타입 사용
    private Float brightnessRatio;
}

package com.smartfarm.smartfarm_server.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class GrowthData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 고유 ID (생장 데이터 식별자)

    private String cropName; // 어떤 작물의 생장 데이터인지 (CropInfo의 crop 필드와 연결 가능)

    private Double growthIndex; // 생장 수치 값 (예: 7.5157)

    @Column(columnDefinition = "TIMESTAMP") // DB 컬럼 타입을 TIMESTAMP로 명시 (선택 사항)
    private LocalDateTime timestamp;

}
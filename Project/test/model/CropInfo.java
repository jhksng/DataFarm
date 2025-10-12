package com.smartfarm.smartfarm_server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Date;
import lombok.Data; // 롬복 Data 애너테이션을 import 합니다.

@Entity
@Data // 이 애너테이션을 추가하면 getter, setter가 자동으로 생성됩니다.
public class CropInfo {

    @Id
    private String crop; // 작물 이름

    private Double targetTemp; // 온도
    private Double targetHumi; // 습도
    private Double targetSoil; // 토양습도
    private Double targetLight; // 조도시간
    private Date harvestDate; // 수확시기

    private boolean isActive; // 현재 사용 중인 작물인지 여부

}
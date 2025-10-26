package com.smartfarm.smartfarm_server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Date; // Date import 확인
import lombok.Data;

@Entity
@Data
public class CropInfo {

    @Id
    private String crop;

    private Double targetTemp;
    private Double targetHumi;
    private Double targetSoil;
    private Double targetLight;
    private Date harvestDate;

    private Date startDate; // 작물 재배 시작일

    private boolean isActive;
}
package com.smartfarm.smartfarm_server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Date;
import lombok.Data;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Data
public class ModuleStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private String moduleName;
    private Integer status;
    private String command;
    private Date commandTime;
    private Date lastOperationTime; // 쿨타임을 위한 마지막 작동 시간

    // 이 줄을 추가하세요.
    private Double accumulatedLightTime; // 누적 조명 시간
}
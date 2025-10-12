package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataApiController {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    // 그래프 데이터를 위한 API (최신 20개 데이터 반환)
    @GetMapping("/recent")
    public List<SensorData> getRecentSensorData() {
        // 최근 20개 데이터를 시간 내림차순으로 가져옵니다.
        return sensorDataRepository.findTop20ByOrderByTimestampDesc();
    }
}
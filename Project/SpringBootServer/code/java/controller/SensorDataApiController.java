package com.smartfarm.smartfarm_server.controller; // 패키지 경로 확인

import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections; // Collections import
import java.util.List;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataApiController {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    /**
     * 그래프 데이터를 위한 API (최신 20개 데이터 반환 - 시간 오름차순 정렬)
     */
    @GetMapping("/recent")
    public List<SensorData> getRecentSensorData() {
        // DB에서 최신 20개 가져오기 (시간 내림차순)
        List<SensorData> recentData = sensorDataRepository.findTop20ByOrderByTimestampDesc();
        // 그래프 X축 순서를 위해 시간 오름차순으로 정렬해서 반환
        Collections.reverse(recentData); // 리스트 순서 뒤집기
        return recentData;
    }

    // /hourly, /half-daily 엔드포인트 제거
}
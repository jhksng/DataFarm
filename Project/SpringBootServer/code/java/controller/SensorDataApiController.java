package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataApiController {

    private static final Logger logger = LoggerFactory.getLogger(SensorDataApiController.class);

    @Autowired
    private SensorDataRepository sensorDataRepository;

    // ✅ 1분 간격 평균
    @GetMapping("/minute")
    public List<Map<String, Object>> getMinute() {
        List<Object[]> data = sensorDataRepository.findMinuteAvgData();
        logResult("1분 간격", data);
        return convert(data);
    }

    // ✅ 1시간 간격 평균
    @GetMapping("/hourly")
    public List<Map<String, Object>> getHourly() {
        List<Object[]> data = sensorDataRepository.findHourlyAvgData();
        logResult("1시간 간격", data);
        return convert(data);
    }

    // ✅ 1일 간격 평균
    @GetMapping("/daily")
    public List<Map<String, Object>> getDaily() {
        List<Object[]> data = sensorDataRepository.findDailyAvgData();
        logResult("1일 간격", data);
        return convert(data);
    }

    // ✅ 1주 간격 평균
    @GetMapping("/weekly")
    public List<Map<String, Object>> getWeekly() {
        List<Object[]> data = sensorDataRepository.findWeeklyAvgData();
        logResult("1주 간격", data);
        return convert(data);
    }

    // ✅ 1달 간격 평균
    @GetMapping("/monthly")
    public List<Map<String, Object>> getMonthly() {
        List<Object[]> data = sensorDataRepository.findMonthlyAvgData();
        logResult("1달 간격", data);
        return convert(data);
    }

    // ✅ 결과 변환 공통 함수
    private List<Map<String, Object>> convert(List<Object[]> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", row[0]);
            map.put("temperature", row[1]);
            map.put("humidity", row[2]);
            map.put("soilMoisture", row[3]);
            map.put("waterLevel", row[4]);
            result.add(map);
        }
        return result;
    }

    // ✅ 로그 출력 함수
    private void logResult(String label, List<Object[]> data) {
        if (data.isEmpty()) {
            logger.warn("⚠️ [{}] 데이터 없음 — 최근 구간에 데이터 존재하지 않음", label);
        } else {
            logger.info("✅ [{}] 결과 행 수: {}", label, data.size());
        }
    }
}

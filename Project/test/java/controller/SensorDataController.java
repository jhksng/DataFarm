package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Optional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class SensorDataController {

    private final SensorDataRepository sensorDataRepository;

    public SensorDataController(SensorDataRepository sensorDataRepository) {
        this.sensorDataRepository = sensorDataRepository;
    }

    /*@GetMapping("/show_sensor_data")
    public String getSensorData(@RequestParam(defaultValue = "0") int page, Model model) {
        // 1. 가장 최신 센서 데이터 1개 불러오기
        Optional<SensorData> latestData = sensorDataRepository.findTopByOrderByTimestampDesc();
        latestData.ifPresent(data -> model.addAttribute("latestSensorData", data));

        // 2. 페이지네이션을 위한 데이터 불러오기
        // 페이지당 50개의 데이터를 시간 내림차순으로 정렬
        Pageable pageable = PageRequest.of(page, 50, Sort.by("timestamp").descending());
        Page<SensorData> pagedData = sensorDataRepository.findAllBy(pageable);

        // 모델에 데이터 추가
        model.addAttribute("sensorDataList", pagedData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pagedData.getTotalPages());

        return "show_sensor_data";
    }
*/

    @GetMapping("/show_sensor_data")
    public String getSensorData(@RequestParam(defaultValue = "0") int page, Model model) {
        // 1. 가장 최신 센서 데이터 1개 불러오기
        Optional<SensorData> latestData = sensorDataRepository.findTopByOrderByTimestampDesc();
        latestData.ifPresent(data -> model.addAttribute("latestSensorData", data));

        // 2. 페이지네이션을 위한 데이터 불러오기
        Pageable pageable = PageRequest.of(page, 50, Sort.by("timestamp").descending());
        Page<SensorData> pagedData = sensorDataRepository.findAllBy(pageable);

        // 모든 데이터 KST로 변환
        pagedData.getContent().forEach(data -> {
            if (data.getTimestamp() != null) {
                data.setTimestamp(
                        data.getTimestamp().atZone(ZoneId.of("UTC"))
                                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                                .toLocalDateTime()
                );
            }
        });

        model.addAttribute("sensorDataList", pagedData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pagedData.getTotalPages());

        return "show_sensor_data";
    }

}
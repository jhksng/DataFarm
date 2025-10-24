package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class SensorDataController {

    private final SensorDataRepository sensorDataRepository;

    @Autowired
    public SensorDataController(SensorDataRepository sensorDataRepository) {
        this.sensorDataRepository = sensorDataRepository;
    }

    @GetMapping("/show_sensor_data")
    public String showSensorData(Model model) {
        Optional<SensorData> latestData = sensorDataRepository.findTopByOrderByTimestampDesc();
        latestData.ifPresent(data -> model.addAttribute("latestSensorData", data));
        return "show_sensor_data";
    }

    @GetMapping("/sensor_history")
    public String showSensorHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false, defaultValue = "false") boolean filterDateEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean filterTempEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean filterHumiEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean filterSoilEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean filterWaterEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean filterDeviceEnabled,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Double minTemp,
            @RequestParam(required = false) Double maxTemp,
            @RequestParam(required = false) Double minHumi,
            @RequestParam(required = false) Double maxHumi,
            @RequestParam(required = false) Double minSoil,
            @RequestParam(required = false) Double maxSoil,
            @RequestParam(required = false) Integer minWater,
            @RequestParam(required = false) Integer maxWater,
            @RequestParam(required = false) String deviceId,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        Specification<SensorData> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDateEnabled) {
                try {
                    LocalDateTime start = (startDate != null && !startDate.isEmpty()) ? LocalDateTime.parse(startDate) : null;
                    LocalDateTime end = (endDate != null && !endDate.isEmpty()) ? LocalDateTime.parse(endDate) : null;
                    if (start != null && end != null) predicates.add(criteriaBuilder.between(root.get("timestamp"), start, end));
                    else if (start != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), start));
                    else if (end != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), end));
                } catch (DateTimeParseException e) {
                    model.addAttribute("parsingError", "날짜 형식이 잘못되었습니다. (YYYY-MM-DDTHH:MM)");
                    // log.error("Date parsing error: {}", e.getMessage()); // 에러 로그도 제거 (필요하면 유지)
                }
            }
            if (filterTempEnabled) {
                if (minTemp != null && maxTemp != null) predicates.add(criteriaBuilder.between(root.get("temperature"), minTemp, maxTemp));
                else if (minTemp != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("temperature"), minTemp));
                else if (maxTemp != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("temperature"), maxTemp));
            }
            if (filterHumiEnabled) {
                if (minHumi != null && maxHumi != null) predicates.add(criteriaBuilder.between(root.get("humidity"), minHumi, maxHumi));
                else if (minHumi != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("humidity"), minHumi));
                else if (maxHumi != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("humidity"), maxHumi));
            }
            if (filterSoilEnabled) {
                if (minSoil != null && maxSoil != null) predicates.add(criteriaBuilder.between(root.get("soilMoisture"), minSoil, maxSoil));
                else if (minSoil != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("soilMoisture"), minSoil));
                else if (maxSoil != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("soilMoisture"), maxSoil));
            }
            if (filterWaterEnabled) {
                if (minWater != null && maxWater != null) predicates.add(criteriaBuilder.between(root.get("waterLevel"), minWater, maxWater));
                else if (minWater != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("waterLevel"), minWater));
                else if (maxWater != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("waterLevel"), maxWater));
            }
            if (filterDeviceEnabled && deviceId != null && !deviceId.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("raspberryId"), deviceId.trim()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<SensorData> sensorDataPage = sensorDataRepository.findAll(spec, pageable);

        model.addAttribute("sensorDataList", sensorDataPage.getContent());
        model.addAttribute("currentPage", sensorDataPage.getNumber());
        model.addAttribute("totalPages", sensorDataPage.getTotalPages());
        model.addAttribute("size", size);
        model.addAttribute("filterDateEnabled", filterDateEnabled);
        model.addAttribute("filterTempEnabled", filterTempEnabled);
        model.addAttribute("filterHumiEnabled", filterHumiEnabled);
        model.addAttribute("filterSoilEnabled", filterSoilEnabled);
        model.addAttribute("filterWaterEnabled", filterWaterEnabled);
        model.addAttribute("filterDeviceEnabled", filterDeviceEnabled);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minTemp", minTemp);
        model.addAttribute("maxTemp", maxTemp);
        model.addAttribute("minHumi", minHumi);
        model.addAttribute("maxHumi", maxHumi);
        model.addAttribute("minSoil", minSoil);
        model.addAttribute("maxSoil", maxSoil);
        model.addAttribute("minWater", minWater);
        model.addAttribute("maxWater", maxWater);
        model.addAttribute("deviceId", deviceId);

        return "sensor_history";
    }
}
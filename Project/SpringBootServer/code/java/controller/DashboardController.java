package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import com.smartfarm.smartfarm_server.service.GrowthAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired
    private CropInfoRepository cropInfoRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ModuleStatusRepository moduleStatusRepository;

    @Autowired
    private GrowthAnalysisService growthAnalysisService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // ── 공통 카드 데이터
        model.addAttribute("latestSensorData",
                sensorDataRepository.findTopByOrderByTimestampDesc().orElse(null));
        model.addAttribute("moduleStatusList", moduleStatusRepository.findAll());

        // 1️⃣ 활성 작물 확인
        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();
        if (activeCropOpt.isEmpty()) {
            model.addAttribute("error", "현재 활성화된 작물이 없습니다.");
            emptyModel(model);
            return "index";
        }

        CropInfo activeCrop = activeCropOpt.get();
        String cropName = activeCrop.getCrop();
        model.addAttribute("activeCropInfo", activeCrop);

        // 2️⃣ 최근 48개 이미지 가져오기 (그래프용)
        List<Photo> photoList = photoRepository.findTop48ByCropNameOrderByUploadDateDesc(cropName);
        if (photoList == null || photoList.isEmpty()) {
            emptyModel(model);
            return "index";
        }

        List<Photo> sortedPhotos = photoList.stream()
                .sorted(Comparator.comparing(Photo::getUploadDate))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        List<String> growthLabels = sortedPhotos.stream()
                .map(p -> p.getUploadDate() != null ? p.getUploadDate().format(formatter) : "N/A")
                .collect(Collectors.toList());

        List<Double> growthValues = sortedPhotos.stream()
                .map(Photo::getBrightnessRatio)
                .filter(Objects::nonNull)
                .map(Float::doubleValue)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                .collect(Collectors.toList());

        model.addAttribute("growthLabels", growthLabels);
        model.addAttribute("growthValues", growthValues);

        // ✅ 3️⃣ 하루 단위 성장률 계산 (전일 평균 vs 금일 평균)
        Map<String, Double> growthStats = growthAnalysisService.calculateDailyGrowthChange(cropName);
        Double dayGrowthChange = growthStats.get("growthChangePercentage");
        Double yesterdayAvg = growthStats.get("yesterdayAvg");
        Double todayAvg = growthStats.get("todayAvg");

        model.addAttribute("growthChangePercentageCalendar", dayGrowthChange);
        model.addAttribute("yesterdayAvg", yesterdayAvg);
        model.addAttribute("todayAvg", todayAvg);
        model.addAttribute("window", "last48_today_vs_yesterday");
        model.addAttribute("timezone", "UTC");

        // ✅ 시각화용: 마지막 이미지 기준 값도 표시 (현재 잎 면적 지표)
        Double latestGrowthIndex = !growthValues.isEmpty() ? growthValues.get(growthValues.size() - 1) : null;
        model.addAttribute("latestGrowthIndex", latestGrowthIndex);

        // ⚙️ “직전 사진 대비” 변화율 계산 로직 제거 (노이즈성)
        // model.addAttribute("growthChangePercentage", null); // 완전히 제거해도 무방

        return "index";
    }

    // ---- Helper ----
    private void emptyModel(Model model) {
        model.addAttribute("growthLabels", Collections.emptyList());
        model.addAttribute("growthValues", Collections.emptyList());
        model.addAttribute("latestGrowthIndex", null);
        model.addAttribute("growthChangePercentageCalendar", null);
        model.addAttribute("yesterdayAvg", null);
        model.addAttribute("todayAvg", null);
    }
}

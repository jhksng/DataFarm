package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired
    private CropInfoRepository cropInfoRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // 1️⃣ 활성 작물 정보 가져오기
        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();

        if (activeCropOpt.isEmpty()) {
            model.addAttribute("error", "현재 활성화된 작물이 없습니다.");
            model.addAttribute("growthLabels", Collections.emptyList());
            model.addAttribute("growthValues", Collections.emptyList());
            model.addAttribute("latestGrowthIndex", null);
            model.addAttribute("growthChangePercentage", null);
            return "index";
        }

        CropInfo activeCrop = activeCropOpt.get();
        String cropName = activeCrop.getCrop();
        model.addAttribute("activeCropInfo", activeCrop);

        // 2️⃣ 최근 10개 brightnessRatio 데이터 조회
        List<Photo> photoList = photoRepository.findTop10ByCropNameOrderByUploadDateAsc(cropName);

        // photoList가 비어있을 경우 바로 반환
        if (photoList == null || photoList.isEmpty()) {
            model.addAttribute("growthLabels", Collections.emptyList());
            model.addAttribute("growthValues", Collections.emptyList());
            model.addAttribute("latestGrowthIndex", null);
            model.addAttribute("growthChangePercentage", null);
            return "index";
        }

        // 3️⃣ 그래프용 데이터 생성
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");

        List<String> growthLabels = photoList.stream()
                .map(p -> p.getUploadDate() != null ? p.getUploadDate().format(formatter) : "N/A")
                .collect(Collectors.toList());

        List<Double> growthValues = photoList.stream()
                .map(Photo::getBrightnessRatio)
                .filter(Objects::nonNull)
                .map(Float::doubleValue)
                .collect(Collectors.toList());

        model.addAttribute("growthLabels", growthLabels);
        model.addAttribute("growthValues", growthValues);

        // 4️⃣ 최신 생장률 / 변화율 계산 (데이터 있을 때만)
        Double latestGrowthIndex = null;
        Double growthChangePercentage = null;

        if (growthValues != null && !growthValues.isEmpty()) {
            latestGrowthIndex = growthValues.get(growthValues.size() - 1);

            if (growthValues.size() >= 2) {
                double latest = growthValues.get(growthValues.size() - 1);
                double previous = growthValues.get(growthValues.size() - 2);
                if (previous != 0) {
                    growthChangePercentage = ((latest - previous) / previous) * 100.0;
                }
            }
        }

        model.addAttribute("latestGrowthIndex", latestGrowthIndex);
        model.addAttribute("growthChangePercentage", growthChangePercentage);

        return "index";
    }
}

package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.model.ModuleStatus;
import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class RootController {

    private final SensorDataRepository sensorDataRepository;
    private final CropInfoRepository cropInfoRepository;
    private final ModuleStatusRepository moduleStatusRepository;
    private final PhotoRepository photoRepository;

    @Autowired
    public RootController(SensorDataRepository sensorDataRepository,
                          CropInfoRepository cropInfoRepository,
                          ModuleStatusRepository moduleStatusRepository,
                          PhotoRepository photoRepository) {
        this.sensorDataRepository = sensorDataRepository;
        this.cropInfoRepository = cropInfoRepository;
        this.moduleStatusRepository = moduleStatusRepository;
        this.photoRepository = photoRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {

        // ✅ 1️⃣ 최신 센서 데이터, 작물 정보, 모듈 상태 불러오기
        model.addAttribute("latestSensorData",
                sensorDataRepository.findTopByOrderByTimestampDesc().orElse(null));

        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();
        model.addAttribute("activeCropInfo", activeCropOpt.orElse(null));

        model.addAttribute("moduleStatusList", moduleStatusRepository.findAll());

        // ✅ 2️⃣ 생장률(brightness_ratio) 그래프용 데이터 준비
        Double latestGrowthIndex = null;
        Double growthChangePercentage = null;
        List<Double> growthValues = new ArrayList<>();
        List<String> growthLabels = new ArrayList<>();

        if (activeCropOpt.isPresent()) {
            String cropName = activeCropOpt.get().getCrop();

            // 최근 48개 데이터 (2일치)
            List<Photo> photoList = photoRepository.findTop48ByCropNameOrderByUploadDateDesc(cropName);

            if (photoList != null && !photoList.isEmpty()) {
                // 업로드 순으로 정렬 (그래프용)
                List<Photo> sortedPhotos = photoList.stream()
                        .sorted(Comparator.comparing(Photo::getUploadDate))
                        .collect(Collectors.toList());

                // brightnessRatio 리스트
                growthValues = sortedPhotos.stream()
                        .map(Photo::getBrightnessRatio)
                        .filter(Objects::nonNull)
                        .map(Float::doubleValue)
                        .collect(Collectors.toList());

                // 날짜 라벨
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
                growthLabels = sortedPhotos.stream()
                        .map(p -> p.getUploadDate() != null ? p.getUploadDate().format(formatter) : "N/A")
                        .collect(Collectors.toList());

                // 최신 생장률 (마지막 값)
                if (!growthValues.isEmpty()) {
                    latestGrowthIndex = growthValues.get(growthValues.size() - 1);
                }

                // ✅ 전일 대비 변화율 계산 (최근 24개 vs 그 전 24개)
                if (growthValues.size() >= 48) {
                    List<Double> prevDay = growthValues.subList(growthValues.size() - 48, growthValues.size() - 24);
                    List<Double> today = growthValues.subList(growthValues.size() - 24, growthValues.size());

                    double prevAvg = prevDay.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double todayAvg = today.stream().mapToDouble(Double::doubleValue).average().orElse(0);

                    if (prevAvg != 0) {
                        growthChangePercentage = ((todayAvg - prevAvg) / prevAvg) * 100.0;
                    }
                }
            }
        }

        // ✅ 3️⃣ 모델에 데이터 추가 (Thymeleaf에서 사용)
        model.addAttribute("latestGrowthIndex", latestGrowthIndex);
        model.addAttribute("growthChangePercentage", growthChangePercentage);
        model.addAttribute("growthValues", growthValues);
        model.addAttribute("growthLabels", growthLabels);

        // ✅ 4️⃣ index.html로 반환
        return "index";
    }

    @GetMapping("/camera")
    public String cameraPage() {
        return "camera";
    }

    @GetMapping("/chatbot")
    public String chatbotPage() {
        return "chatbot";
    }

    @GetMapping("/harvest")
    public String harvestPage() {
        return "harvest";
    }
}

package com.smartfarm.smartfarm_server.controller; // 패키지 경로 확인

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.GrowthData; // GrowthData 모델 import
import com.smartfarm.smartfarm_server.model.ModuleStatus;
import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.GrowthDataRepository; // GrowthDataRepository import
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter; // 날짜 포맷용
import java.util.Collections;
import java.util.Comparator; // 데이터 정렬용 추가
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class RootController {

    private final SensorDataRepository sensorDataRepository;
    private final CropInfoRepository cropInfoRepository;
    private final ModuleStatusRepository moduleStatusRepository;
    private final GrowthDataRepository growthDataRepository; // GrowthDataRepository 주입

    @Autowired
    public RootController(SensorDataRepository sensorDataRepository,
                          CropInfoRepository cropInfoRepository,
                          ModuleStatusRepository moduleStatusRepository,
                          GrowthDataRepository growthDataRepository) { // 생성자에 추가
        this.sensorDataRepository = sensorDataRepository;
        this.cropInfoRepository = cropInfoRepository;
        this.moduleStatusRepository = moduleStatusRepository;
        this.growthDataRepository = growthDataRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        // --- 최신 센서 데이터, 활성 작물, 모듈 상태 조회 ---
        Optional<SensorData> latestDataOpt = sensorDataRepository.findTopByOrderByTimestampDesc();
        model.addAttribute("latestSensorData", latestDataOpt.orElse(null));

        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();
        model.addAttribute("activeCropInfo", activeCropOpt.orElse(null));

        List<ModuleStatus> moduleStatusList = moduleStatusRepository.findAll();
        model.addAttribute("moduleStatusList", moduleStatusList);

        // --- 생장 데이터 관련 로직 수정 ---
        Double latestGrowthIndex = null; // 최신 생장 수치 변수 추가
        Double growthChangePercentage = null; // 전일 대비 변화율 변수 추가
        List<Double> growthValues = Collections.emptyList();
        List<String> growthLabels = Collections.emptyList();

        if (activeCropOpt.isPresent()) {
            CropInfo activeCrop = activeCropOpt.get();

            // 1. 현재 활성 작물의 최근 생장 데이터 조회 (그래프용 + 변화율 계산용)
            //    최소 2개 이상, 시간 내림차순으로 가져와서 최신값과 이전값을 쉽게 찾음
            //    GrowthDataRepository에 findTop10ByCropNameOrderByTimestampDesc 메서드가 필요
            List<GrowthData> recentGrowthDataDesc = growthDataRepository.findTop10ByCropNameOrderByTimestampDesc(activeCrop.getCrop());

            if (recentGrowthDataDesc != null && !recentGrowthDataDesc.isEmpty()) {

                // 그래프용 데이터 준비 (시간 오름차순으로 다시 정렬)
                List<GrowthData> recentGrowthDataAsc = recentGrowthDataDesc.stream()
                        .sorted(Comparator.comparing(GrowthData::getTimestamp)) // 오름차순 정렬
                        .collect(Collectors.toList());

                growthValues = recentGrowthDataAsc.stream()
                        .map(GrowthData::getGrowthIndex) // GrowthData에 getGrowthIndex() 필요
                        .collect(Collectors.toList());
                growthLabels = recentGrowthDataAsc.stream()
                        .map(data -> data.getTimestamp() // GrowthData에 getTimestamp() 필요 (LocalDateTime 가정)
                                .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")))
                        .collect(Collectors.toList());

                // 2. 가장 최근 생장 수치 추출 (내림차순 리스트의 첫번째 값)
                latestGrowthIndex = recentGrowthDataDesc.get(0).getGrowthIndex();

                // 3. 전일 대비 변화율 계산 (데이터가 2개 이상 있을 때)
                if (recentGrowthDataDesc.size() >= 2) {
                    Double previousGrowthIndex = recentGrowthDataDesc.get(1).getGrowthIndex(); // 두 번째로 최신 값
                    // 이전 값이 null이 아니고 0이 아닐 때만 계산 (0으로 나누기 방지)
                    if (previousGrowthIndex != null && previousGrowthIndex != 0.0) {
                        growthChangePercentage = ((latestGrowthIndex - previousGrowthIndex) / previousGrowthIndex) * 100.0;
                    }
                }
            }
        }

        model.addAttribute("latestGrowthIndex", latestGrowthIndex); // 최신 생장 수치 추가
        model.addAttribute("growthChangePercentage", growthChangePercentage); // 변화율 모델에 추가
        model.addAttribute("growthValues", growthValues);
        model.addAttribute("growthLabels", growthLabels);
        // --- 수정 끝 ---

        return "index"; // templates/index.html 반환
    }

    @GetMapping("/camera")
    public String cameraPage() {
        return "camera";
    }

    @GetMapping("/chatbot")
    public String test() {
        return "chatbot"; // templates/test.html 렌더링
    }

    @GetMapping("/harvest")
    public String harvest() {
        return "harvest"; // templates/harvest.html 렌더링
    }

}

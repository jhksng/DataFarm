package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 이 컨트롤러는 센서 데이터와 관련된 웹 페이지를 반환하는 역할을 합니다.
 * 사용자가 특정 URL로 접속하면, 이 컨트롤러의 메소드가 실행되어 해당하는 HTML 파일을 보여줍니다.
 */
@Controller
public class SensorDataController {

    // final 키워드를 사용하여 SensorDataRepository가 변경되지 않도록 합니다.
    // 생성자를 통해 의존성을 주입받습니다 (Dependency Injection).
    private final SensorDataRepository sensorDataRepository;

    public SensorDataController(SensorDataRepository sensorDataRepository) {
        this.sensorDataRepository = sensorDataRepository;
    }

    /**
     * '센서 데이터 현황' 페이지를 처리하는 메소드입니다. (최신값 카드 + 그래프 표시)
     * 사용자가 "/show_sensor_data" URL로 GET 요청을 보내면 이 메소드가 호출됩니다.
     * @param model 뷰(HTML)에 데이터를 전달하기 위한 Spring 객체
     * @return "show_sensor_data" 라는 이름의 HTML 템플릿을 반환합니다. (templates/show_sensor_data.html)
     */
    @GetMapping("/show_sensor_data")
    public String showSensorData(Model model) {

        Optional<SensorData> latestData = sensorDataRepository.findTopByOrderByTimestampDesc();
        latestData.ifPresent(data -> model.addAttribute("latestSensorData", data));
        return "show_sensor_data"; // templates/show_sensor_data.html 페이지를 렌더링하여 사용자에게 보여줍니다.
    }

    /**
     * '전체 센서 기록' 페이지를 처리하고, 필터링 기능을 수행하는 메소드입니다.
     * @param startDate 검색 시작일 (yyyy-MM-dd'T'HH:mm 형식)
     * @param endDate 검색 종료일 (yyyy-MM-dd'T'HH:mm 형식)
     * @param deviceId 검색할 장치 ID
     * @param page 현재 페이지 번호
     * @param model 뷰에 데이터를 전달하기 위한 객체
     * @return "sensor_history" HTML 템플릿
     */
    @GetMapping("/sensor_history")
    public String showSensorHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        // 1. 페이지네이션 설정을 생성합니다. 한 페이지에 30개의 데이터를 최신순으로 표시합니다.
        Pageable pageable = PageRequest.of(page, 30, Sort.by("timestamp").descending());
        // 2. 동적 쿼리를 생성하기 위한 Specification 객체를 만듭니다.
        //    Specification.where(null)은 아무 조건도 없는 기본 상태입니다.
        Specification<SensorData> spec = Specification.where(null);
        // 3. 필터 조건들을 Specification에 추가합니다.
        //    각 파라미터가 null이 아닐 경우에만 검색 조건이 추가됩니다.

        // 시작일
        if (startDate != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), startDate));
        }
        // 종료일
        if (endDate != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), endDate));
        }
        if (deviceId != null && !deviceId.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("raspberryId"), deviceId));
        }

        // 4. 완성된 Specification과 Pageable 객체로 데이터베이스를 조회합니다.
        Page<SensorData> sensorDataPage = sensorDataRepository.findAll(spec, pageable);

        // 5. 뷰(HTML)에서 사용할 수 있도록 모델에 데이터를 추가합니다.
        model.addAttribute("sensorDataList", sensorDataPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", sensorDataPage.getTotalPages());

        // 검색 후에도 필터 입력 값을 유지하기 위해 파라미터를 모델에 다시 전달합니다.
        // (Thymeleaf의 param 객체를 사용하면 이 코드는 필수는 아니지만, 명시적으로 추가하는 것이 좋습니다.)
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("deviceId", deviceId);

        return "sensor_history";
    }
}
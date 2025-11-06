package com.smartfarm.smartfarm_server.service;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.model.ModuleStatus;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class FarmControlScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FarmControlScheduler.class);

    @Autowired
    private CropInfoRepository cropInfoRepository;
    @Autowired
    private SensorDataRepository sensorDataRepository;
    @Autowired
    private ModuleStatusRepository moduleStatusRepository;
    @Autowired
    private MqttPublisherService mqttPublisherService;

    private final String DEVICE_ID = "raspi-01";
    // 상태 관리
    private final Map<String, Object> stateStore = new ConcurrentHashMap<>();

    // 히터 과부하 관리
    private long getHeaterRestUntil() {
        return (long) stateStore.getOrDefault("heaterRestUntilTime", 0L);
    }

    private void setHeaterRestUntil(long time) {
        stateStore.put("heaterRestUntilTime", time);
    }

    private int getActivationsInLast(long durationMillis) {
        @SuppressWarnings("unchecked")
        List<Long> history = (List<Long>) stateStore.getOrDefault("heaterActivationHistory", new CopyOnWriteArrayList<Long>());
        long cutoffTime = System.currentTimeMillis() - durationMillis;
        List<Long> recent = history.stream().filter(t -> t >= cutoffTime).collect(Collectors.toList());
        stateStore.put("heaterActivationHistory", recent);
        return recent.size();
    }

    private void recordHeaterActivation(long currentTime) {
        @SuppressWarnings("unchecked")
        List<Long> history = (List<Long>) stateStore.getOrDefault("heaterActivationHistory", new CopyOnWriteArrayList<Long>());
        history.add(currentTime);
        stateStore.put("heaterActivationHistory", history);
    }



    // led 촬영 오버라이드
    private final String PHOTO_FORCE_OFF_TIME_KEY = "photoForceOffTime";

    private long getPhotoForceOffTime() {
        return (long) stateStore.getOrDefault(PHOTO_FORCE_OFF_TIME_KEY, 0L);
    }

    private void setPhotoForceOffTime(long time) {
        stateStore.put(PHOTO_FORCE_OFF_TIME_KEY, time);
    }

    public void enablePhotoShootLedOn(int durationMinutes) {
        long newForceOffTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        setPhotoForceOffTime(newForceOffTime);
        logger.info("LED 촬영 오버라이드 {}분 활성화 (종료: {})", durationMinutes, new Date(newForceOffTime));
    }

    // 수동 제어 오버라이드
    private final String MANUAL_OVERRIDE_KEY_PREFIX = "manualOverride_";

    public void enableManualOverride(String moduleName, int durationMinutes) {
        long until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        stateStore.put(MANUAL_OVERRIDE_KEY_PREFIX + moduleName, until);
        logger.warn("[{}] 모듈 수동 오버라이드 {}분 활성화 (종료시각: {}).", moduleName, durationMinutes, new Date(until));
    }

    private boolean isManualOverrideActive(String moduleName) {
        long until = (long) stateStore.getOrDefault(MANUAL_OVERRIDE_KEY_PREFIX + moduleName, 0L);
        return System.currentTimeMillis() < until;
    }

    public void disableManualOverride(String moduleName) {
        String key = MANUAL_OVERRIDE_KEY_PREFIX + moduleName;
        if (stateStore.containsKey(key)) {
            stateStore.remove(key);
            logger.info("[{}] 모듈 수동 오버라이드 해제 완료.", moduleName);
        } else {
            logger.info("[{}] 모듈은 오버라이드 상태가 아닙니다.", moduleName);
        }
    }
    // 워터펌프 쿨타임
    private long getPumpRestUntil() {
        return (long) stateStore.getOrDefault("pumpRestUntil", 0L);
    }

    private void setPumpRestUntil(long time) {
        stateStore.put("pumpRestUntil", time);
    }


    // 스케줄링 로직
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void resetAccumulatedLightTime() {
        moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "led")
                .ifPresent(led -> {
                    led.setAccumulatedLightTime(0.0);
                    moduleStatusRepository.save(led);
                    logger.info("매일 8시 누적조명시간 초기화 완료");
                });
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndControlFarm() {
        logger.info("[FarmControlScheduler] 1분 제어 시작");

        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();
        if (activeCropOpt.isEmpty()) {
            logger.warn("활성 작물이 없습니다.");
            return;
        }
        CropInfo activeCrop = activeCropOpt.get();

        Optional<SensorData> sensorOpt = sensorDataRepository.findTopByOrderByTimestampDesc();
        if (sensorOpt.isEmpty()) {
            logger.warn("센서 데이터 없음.");
            return;
        }
        SensorData data = sensorOpt.get();

        ModuleStatus heater = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "heater").orElse(null);
        ModuleStatus pump = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "waterPump").orElse(null);
        ModuleStatus led = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "led").orElse(null);
        ModuleStatus coolerA = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "coolerA").orElse(null);
        ModuleStatus coolerB = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "coolerB").orElse(null);

        long now = System.currentTimeMillis();

        // LED 자동 제어
        if (led != null) {
            long currentTime = System.currentTimeMillis();

            // 수동 오버라이드 확인
            if (isManualOverrideActive("led")) {
                logger.warn("[LED] 수동 오버라이드 활성 중 - 자동 제어 무시");
            } else {
                // 촬영 오버라이드 확인
                long photoForceUntil = getPhotoForceOffTime();
                if (currentTime < photoForceUntil) {
                    updateModuleStatus("led", 1, "on", led.getAccumulatedLightTime());
                    logger.warn("[LED] 촬영 오버라이드 중 - 강제 ON 유지 (종료: {})", new Date(photoForceUntil));
                } else {
                    // 누적 조명시간 계산 (ON 상태일 때만 1분 단위로 고정 적산)
                    double accLight = led.getAccumulatedLightTime();

                    if (led.getStatus() == 1) {
                        // 1분 = 1/60 시간 (0.0166667h)
                        double elapsedHours = 1.0 / 60.0;
                        accLight += elapsedHours;

                        // commandTime은 단순히 기준 갱신용 (다음 루프 대비)
                        led.setCommandTime(new Date());

                        logger.info(String.format("[LED] 누적 조명시간 +0.0167h → 총 %.3fh", accLight));

                    }

                    // 목표 조명시간 기준으로 제어 판단
                    double targetLight = activeCrop.getTargetLight();
                    double lowerBound = targetLight;
                    double upperBound = targetLight + 1.0;

                    int newStatus = led.getStatus();
                    String command = led.getCommand();

                    if (accLight < lowerBound) {
                        newStatus = 1;
                        command = "on";
                    } else if (accLight > upperBound) {
                        newStatus = 0;
                        command = "off";
                    }

                    // DB + MQTT 갱신
                    updateModuleStatus("led", newStatus, command, accLight);
                }
            }
        }


        // 워터펌프
        if (pump != null) {
            if (isManualOverrideActive("waterPump")) {
                logger.warn("waterPump 오버라이드 활성 중 - 자동 제어 건너뜀");
            } else {
                long nowTime = System.currentTimeMillis();
                long restUntil = getPumpRestUntil();

                if (nowTime < restUntil) {
                    long remainHours = (restUntil - nowTime) / (1000 * 60 * 60);
                    logger.info("[워터펌프] 쿨타임 중 - 남은 {}시간", remainHours);
                    updateModuleStatus("waterPump", 0, "off", 0.0);
                    return;
                }

                double soil = data.getSoilMoisture();
                if (soil < activeCrop.getTargetSoil() - 10) {
                    updateModuleStatus("waterPump", 1, "on", 0.0);
                    // 2일 쿨타임 설정
                    setPumpRestUntil(nowTime + TimeUnit.DAYS.toMillis(2));
                    logger.info("[워터펌프] 작동 후 2일 쿨타임 진입 (종료 시각: {})", new Date(nowTime + TimeUnit.DAYS.toMillis(2)));
                } else {
                    updateModuleStatus("waterPump", 0, "off", 0.0);
                }
            }
        }


        // 온도 > 습도 > 환기 ( 우선 순위 )
        if (heater != null && coolerA != null && coolerB != null) {

            double temp = data.getTemperature();
            double humi = data.getHumidity();
            double targetTemp = activeCrop.getTargetTemp();
            double targetHumi = activeCrop.getTargetHumi();

            boolean tempLow = temp < targetTemp - 1.0;
            boolean tempHigh = temp > targetTemp + 0.3;
            boolean humiHigh = humi > targetHumi + 10.0;

            long nowMillis = System.currentTimeMillis();

            // 1️⃣ 수동 오버라이드 체크
            if (isManualOverrideActive("heater") || isManualOverrideActive("coolerA") || isManualOverrideActive("coolerB")) {
                logger.warn("🛠️ [모듈 오버라이드 활성 중] 자동 제어 일부 건너뜀");
            } else {

                // =====================================================
                // (1) 온도 제어 - 최우선
                // =====================================================
                if (tempLow) {
                    long nowTime = System.currentTimeMillis();

                    // (A) 과부하 휴식 시간 확인
                    if (nowTime < getHeaterRestUntil()) {
                        long remain = (getHeaterRestUntil() - nowTime) / 1000;
                        logger.warn("[히터] 과부하 보호 중 - 남은 휴식시간: {}초", remain);
                        updateModuleStatus("heater", 0, "off", 0.0);
                        return; // 이번 루프에서는 작동하지 않음
                    }

                    // (B) 히터 작동 시작 기록
                    recordHeaterActivation(nowTime);

                    // 최근 10분간 작동 이력 확인
                    int activeCount = getActivationsInLast(TimeUnit.MINUTES.toMillis(10));

                    if (activeCount >= 10) { // 10분 연속 ON
                        long restUntil = nowTime + TimeUnit.MINUTES.toMillis(3);
                        setHeaterRestUntil(restUntil);
                        logger.warn("[히터] 10분 연속 작동 → 3분간 휴식 진입 (종료시각: {})", new Date(restUntil));
                        updateModuleStatus("heater", 0, "off", 0.0);
                        return;
                    }

                    // 정상 작동
                    logger.info("[온도제어] 온도 {}°C < 목표 {}°C -1.0 → 히터 ON, 쿨러 OFF",
                            String.format("%.2f", temp), String.format("%.2f", targetTemp));

                    updateModuleStatus("heater", 1, "on", 0.0);
                    updateModuleStatus("coolerA", 0, "off", 0.0);
                    updateModuleStatus("coolerB", 0, "off", 0.0);
                }

                // =====================================================
                // (2) 습도 제어 - 온도 정상일 때만
                // =====================================================
                else if (!tempLow && !tempHigh && humiHigh) {
                    logger.info("[습도제어] 습도 {}% > 목표 {}% +10 → 쿨러B ON / 나머지 OFF",
                            String.format("%.1f", humi), String.format("%.1f", targetHumi));

                    updateModuleStatus("heater", 0, "off", 0.0);
                    updateModuleStatus("coolerA", 0, "off", 0.0);
                    updateModuleStatus("coolerB", 1, "on", 0.0);
                }

                // =====================================================
                // (3) 환기 제어 - 온/습도 모두 정상
                // =====================================================
                else if (!tempLow && !tempHigh && !humiHigh) {

                    // 쿨러A: 25분 OFF + 5분 ON
                    long lastStartA = (long) stateStore.getOrDefault("coolerA_lastStart", 0L);
                    long elapsedA = nowMillis - lastStartA;
                    long totalCycleA = TimeUnit.MINUTES.toMillis(30);
                    long onStartA = TimeUnit.MINUTES.toMillis(25);
                    if (elapsedA >= totalCycleA) {
                        stateStore.put("coolerA_lastStart", nowMillis);
                        elapsedA = 0L;
                    }
                    boolean coolerA_On = elapsedA >= onStartA;

                    // 쿨러B: 15분 OFF + 5분 ON
                    long lastStartB = (long) stateStore.getOrDefault("coolerB_lastStart", 0L);
                    long elapsedB = nowMillis - lastStartB;
                    long totalCycleB = TimeUnit.MINUTES.toMillis(20);
                    long onStartB = TimeUnit.MINUTES.toMillis(15);
                    if (elapsedB >= totalCycleB) {
                        stateStore.put("coolerB_lastStart", nowMillis);
                        elapsedB = 0L;
                    }
                    boolean coolerB_On = elapsedB >= onStartB;

                    // 각 쿨러의 쿨타임(OFF 남은 시간) 계산
                    long remainA = (elapsedA < onStartA)
                            ? (onStartA - elapsedA)
                            : 0L;
                    long remainB = (elapsedB < onStartB)
                            ? (onStartB - elapsedB)
                            : 0L;

                    updateModuleStatus("heater", 0, "off", 0.0);
                    updateModuleStatus("coolerA", coolerA_On ? 1 : 0, coolerA_On ? "on" : "off", 0.0);
                    updateModuleStatus("coolerB", coolerB_On ? 1 : 0, coolerB_On ? "on" : "off", 0.0);

                    // 로그 출력 (현재 쿨타임 여부 + 남은 시간 표시)
                    if (!coolerA_On) {
                        logger.info("[coolerA] 쿨타임 진행 중 - 남은 시간: {}분 {}초",
                                remainA / 60000,
                                String.format("%.0f", (remainA % 60000) / 1000.0));
                    }
                    if (!coolerB_On) {
                        logger.info("[coolerB] 쿨타임 진행 중 - 남은 시간: {}분 {}초",
                                remainB / 60000,
                                String.format("%.0f", (remainB % 60000) / 1000.0));

                    }

                    logger.info("[환기제어] 온·습도 정상 → A:{}, B:{} (A {}분/B {}분 경과)",
                            (coolerA_On ? "ON" : "OFF"),
                            (coolerB_On ? "ON" : "OFF"),
                            String.format("%.1f", elapsedA / 60000.0),
                            String.format("%.1f", elapsedB / 60000.0)
                    );
                }
            }
        }
    }


    // 모듈 상태 업데이트
    @Transactional
    public void updateModuleStatus(String moduleName, Integer status, String command, Double accumulatedLightTime) {
        ModuleStatus module = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, moduleName)
                .orElseGet(() -> {
                    ModuleStatus m = new ModuleStatus();
                    m.setDeviceId(DEVICE_ID);
                    m.setModuleName(moduleName);
                    logger.warn("모듈 신규 생성: {}", moduleName);
                    return m;
                });

        Integer previousStatus = module.getStatus();
        boolean changed = !Objects.equals(module.getStatus(), status)
                || !Objects.equals(module.getCommand(), command);


        module.setStatus(status);
        module.setCommand(command);

        // LED 처리 로직
        if ("led".equals(moduleName)) {
            // 자동 제어일 때만 누적시간 갱신
            boolean isAutoControl = accumulatedLightTime != null;

            if (isAutoControl) {
                module.setAccumulatedLightTime(accumulatedLightTime);
            } else {
                // 수동제어나 단순 on/off 시에는 누적시간 그대로 유지
                logger.debug("[LED] 수동 제어 또는 단순 ON/OFF 요청 → 누적 조명시간 변경 안 함");
            }

            // LED가 OFF → ON으로 바뀔 때만 commandTime 갱신
            if (status == 1 && (previousStatus == null || previousStatus == 0)) {
                module.setCommandTime(new Date());
            }
        }

        // 모든 모듈에 대해 ON일 때 마지막 작동 시간 갱신
        if (status == 1) {
            module.setLastOperationTime(new Date());
        }

        // DB 저장
        moduleStatusRepository.save(module);

        // MQTT 전송
        String topic = "farm/" + DEVICE_ID + "/" + moduleName;
        String payload = "{\"command\":\"" + command + "\",\"status\":" + status + "}";
        mqttPublisherService.publish(topic, payload);

        logger.info("[{}] 상태={}, 명령='{}' (MQTT 전송)", moduleName, status, command);
    }
}

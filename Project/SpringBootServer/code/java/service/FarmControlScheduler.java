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

    // =========================================================================================
    // [상태 관리]
    // =========================================================================================
    private final Map<String, Object> stateStore = new ConcurrentHashMap<>();

    // =========================================================================================
    // [히터 과부하 관리]
    // =========================================================================================
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

    // =========================================================================================
    // [쿨러 순환 제어 설정]
    // =========================================================================================
    private final long COOLER_B_CYCLE_DURATION = TimeUnit.MINUTES.toMillis(30);
    private final long COOLER_B_RUN_DURATION = TimeUnit.MINUTES.toMillis(25);
    private final long COOLER_A_CYCLE_DURATION = TimeUnit.MINUTES.toMillis(15);
    private final long COOLER_A_RUN_DURATION = TimeUnit.MINUTES.toMillis(5);

    private long getCoolerLastStartTime(String cooler) {
        return (long) stateStore.getOrDefault(cooler + "LastStartTime", 0L);
    }

    private void setCoolerLastStartTime(String cooler, long time) {
        stateStore.put(cooler + "LastStartTime", time);
    }

    // =========================================================================================
    // [LED 촬영 오버라이드 관리]
    // =========================================================================================
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
        logger.info("📸 LED 촬영 오버라이드 {}분 활성화 (종료: {})", durationMinutes, new Date(newForceOffTime));
    }

    // =========================================================================================
    // [수동 제어 오버라이드 관리] 🧠
    // =========================================================================================
    private final String MANUAL_OVERRIDE_KEY_PREFIX = "manualOverride_";

    public void enableManualOverride(String moduleName, int durationMinutes) {
        long until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        stateStore.put(MANUAL_OVERRIDE_KEY_PREFIX + moduleName, until);
        logger.warn("🛠️ [{}] 모듈 수동 오버라이드 {}분 활성화 (종료시각: {}).", moduleName, durationMinutes, new Date(until));
    }

    private boolean isManualOverrideActive(String moduleName) {
        long until = (long) stateStore.getOrDefault(MANUAL_OVERRIDE_KEY_PREFIX + moduleName, 0L);
        return System.currentTimeMillis() < until;
    }

    public void disableManualOverride(String moduleName) {
        String key = MANUAL_OVERRIDE_KEY_PREFIX + moduleName;
        if (stateStore.containsKey(key)) {
            stateStore.remove(key);
            logger.info("🔓 [{}] 모듈 수동 오버라이드 해제 완료.", moduleName);
        } else {
            logger.info("ℹ️ [{}] 모듈은 오버라이드 상태가 아닙니다.", moduleName);
        }
    }

    // =========================================================================================
    // [스케줄링 로직]
    // =========================================================================================
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void resetAccumulatedLightTime() {
        moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "led")
                .ifPresent(led -> {
                    led.setAccumulatedLightTime(0.0);
                    moduleStatusRepository.save(led);
                    logger.info("✅ 매일 8시 누적조명시간 초기화 완료");
                });
    }

    @Scheduled(fixedRate = 60000)
    public void checkAndControlFarm() {
        logger.info("🌿 [FarmControlScheduler] 1분 제어 시작");

        Optional<CropInfo> activeCropOpt = cropInfoRepository.findByIsActiveTrue();
        if (activeCropOpt.isEmpty()) {
            logger.warn("⚠️ 활성 작물이 없습니다.");
            return;
        }
        CropInfo activeCrop = activeCropOpt.get();

        Optional<SensorData> sensorOpt = sensorDataRepository.findTopByOrderByTimestampDesc();
        if (sensorOpt.isEmpty()) {
            logger.warn("⚠️ 센서 데이터 없음.");
            return;
        }
        SensorData data = sensorOpt.get();

        ModuleStatus heater = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "heater").orElse(null);
        ModuleStatus pump = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "waterPump").orElse(null);
        ModuleStatus led = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "led").orElse(null);
        ModuleStatus coolerA = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "coolerA").orElse(null);
        ModuleStatus coolerB = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, "coolerB").orElse(null);

        long now = System.currentTimeMillis();

        // =====================================================================
// 💡 LED 제어 (정교한 누적 조명 시간 관리 + 오버라이드)
// =====================================================================
        if (led != null) {

            long currentTime = System.currentTimeMillis();

            // 🌙 1️⃣ 수동 오버라이드 확인
            if (isManualOverrideActive("led")) {
                logger.warn("🛠️ [LED] 수동 오버라이드 활성 중 - 자동 제어 무시");
            } else {
                // 📸 2️⃣ 촬영 오버라이드 확인
                long photoForceUntil = getPhotoForceOffTime();
                if (currentTime < photoForceUntil) {
                    updateModuleStatus("led", 1, "on", led.getAccumulatedLightTime());
                    logger.warn("📸 [LED] 촬영 오버라이드 중 - 강제 ON 유지 (종료: {})", new Date(photoForceUntil));
                } else {
                    // 🌤️ 3️⃣ 누적 조명시간 계산 (ON 상태일 때만)
                    double accLight = led.getAccumulatedLightTime();

// 마지막 계산 시각 저장용 키
                    String LED_LAST_UPDATE_KEY = "ledLastUpdateTime";
                    long lastUpdateTime = (long) stateStore.getOrDefault(LED_LAST_UPDATE_KEY, currentTime);
                    long elapsed = currentTime - lastUpdateTime;

// ✅ LED가 켜져 있을 때만 시간 누적 및 기준시각 갱신
                    if (led.getStatus() == 1) {
                        // 💡 1분 이상 지연될 경우에도 1분만 누적되도록 보정
                        long safeElapsed = Math.min(elapsed, 60000);
                        double elapsedHours = safeElapsed / (1000.0 * 60.0 * 60.0);
                        accLight += elapsedHours;
                        stateStore.put(LED_LAST_UPDATE_KEY, currentTime);
                    }

// OFF 상태일 때는 업데이트 시간 갱신하지 않음
                    updateModuleStatus("led", led.getStatus(), led.getCommand(), accLight);

                    // 🌤️ 4️⃣ 목표 조명시간 기준 제어
                    double targetLight = activeCrop.getTargetLight();
                    double lowerBound = targetLight;
                    double upperBound = targetLight + 1.0; // 목표보다 0.5시간 초과 시 끄기

                    int newStatus = led.getStatus();
                    String command = led.getCommand();

                    if (accLight < lowerBound) {
                        newStatus = 1;
                        command = "on";
                        logger.info("💡 [LED] 누적 조명 {}h < 목표 {}h → ON",
                                String.format("%.2f", accLight), String.format("%.2f", targetLight));
                    } else if (accLight > upperBound) {
                        newStatus = 0;
                        command = "off";
                        logger.info("🌑 [LED] 누적 조명 {}h > 목표+2h ({}h) → OFF",
                                String.format("%.2f", accLight), String.format("%.2f", upperBound));
                    }

                    // ✅ DB + MQTT 갱신
                    updateModuleStatus("led", newStatus, command, accLight);
                }
            }
        }


        // =====================================================================
        // 💧 워터펌프 제어
        // =====================================================================
        if (pump != null) {
            if (isManualOverrideActive("waterPump")) {
                logger.warn("🛠️ waterPump 오버라이드 활성 중 - 자동 제어 건너뜀");
            } else {
                double soil = data.getSoilMoisture();
                if (soil < activeCrop.getTargetSoil() - 10) {
                    updateModuleStatus("waterPump", 1, "on", 0.0);
                } else {
                    updateModuleStatus("waterPump", 0, "off", 0.0);
                }
            }
        }

        // =====================================================================
        // 🔥 히터 제어
        // =====================================================================
        if (heater != null) {
            if (isManualOverrideActive("heater")) {
                logger.warn("🛠️ heater 오버라이드 활성 중 - 자동 제어 건너뜀");
            } else {
                if (data.getTemperature() < activeCrop.getTargetTemp() - 1.5) {
                    updateModuleStatus("heater", 1, "on", 0.0);
                } else {
                    updateModuleStatus("heater", 0, "off", 0.0);
                }
            }
        }

        // =====================================================================
        // 🌬️ 쿨러 A 제어
        // =====================================================================
        if (coolerA != null) {
            if (isManualOverrideActive("coolerA")) {
                logger.warn("🛠️ coolerA 오버라이드 활성 중 - 자동 제어 건너뜀");
            } else {
                long lastStart = getCoolerLastStartTime("coolerA");
                long elapsed = now - lastStart;

                if (elapsed >= COOLER_A_CYCLE_DURATION) {
                    // 새로운 주기 시작: 쿨러 ON
                    updateModuleStatus("coolerA", 1, "on", 0.0);
                    setCoolerLastStartTime("coolerA", now);
                    logger.info("🌬️ [coolerA] 주기 시작 - 쿨러 ON (15분 주기)");
                } else if (elapsed >= COOLER_A_RUN_DURATION) {
                    // 가동 시간 초과: 쿨러 OFF
                    updateModuleStatus("coolerA", 0, "off", 0.0);
                }
            }
        }

        // =====================================================================
        // 🌬️ 쿨러 B 제어
        // =====================================================================
        if (coolerB != null) {
            if (isManualOverrideActive("coolerB")) {
                logger.warn("🛠️ coolerB 오버라이드 활성 중 - 자동 제어 건너뜀");
            } else {
                long lastStart = getCoolerLastStartTime("coolerB");
                long nowMillis = System.currentTimeMillis();
                long elapsed = nowMillis - lastStart;

                // 처음 실행 시 기준 시간 기록
                if (lastStart == 0L) {
                    setCoolerLastStartTime("coolerB", nowMillis);
                    lastStart = nowMillis;
                    elapsed = 0L;
                }

                // 한 사이클 = 60분 (60000ms × 60)
                // 총 주기: 60분 (15분 × 4사이클)
                long cycleMillis = TimeUnit.MINUTES.toMillis(60);

// 각 사이클 길이: 15분 = 10분 OFF + 5분 ON
                long cycleUnit = TimeUnit.MINUTES.toMillis(15);
                long onDuration = TimeUnit.MINUTES.toMillis(5);
                long offDuration = TimeUnit.MINUTES.toMillis(10);

// 현재 시간 계산
                long elapsedInCycle = nowMillis - lastStart;
                long elapsedInUnit = elapsedInCycle % cycleUnit; // 현재 사이클 내 경과 시간

// ON/OFF 판정
                boolean shouldBeOn = elapsedInUnit >= offDuration; // 10분 이후 5분만 ON
                int status = shouldBeOn ? 1 : 0;
                String command = shouldBeOn ? "on" : "off";

                updateModuleStatus("coolerB", status, command, 0.0);

// 한 시간(60분)마다 기준 시각 초기화
                if (elapsedInCycle >= cycleMillis) {
                    setCoolerLastStartTime("coolerB", nowMillis);
                }

                logger.info("🌬️ [coolerB] 주기 상태: {} (사이클 진행 {:.1f}분/{})",
                        command.toUpperCase(),
                        elapsedInUnit / 60000.0,
                        (shouldBeOn ? "ON" : "OFF"));
            }
        }
    }


    // =========================================================================================
// [모듈 상태 업데이트 + MQTT 전송]
// =========================================================================================
    @Transactional
    public void updateModuleStatus(String moduleName, Integer status, String command, double accumulatedLightTime) {
        ModuleStatus module = moduleStatusRepository.findByDeviceIdAndModuleName(DEVICE_ID, moduleName)
                .orElseGet(() -> {
                    ModuleStatus m = new ModuleStatus();
                    m.setDeviceId(DEVICE_ID);
                    m.setModuleName(moduleName);
                    logger.warn("⚠️ 모듈 신규 생성: {}", moduleName);
                    return m;
                });

        boolean changed = (module.getStatus() == null || !module.getStatus().equals(status))
                || (module.getCommand() == null || !module.getCommand().equals(command));

        Integer previousStatus = module.getStatus();
        module.setStatus(status);
        module.setCommand(command);

        // ✅ LED 누적 조명 시간 처리
        if ("led".equals(moduleName)) {
            module.setAccumulatedLightTime(accumulatedLightTime);
            // LED가 OFF → ON 으로 바뀔 때만 commandTime 갱신
            if (status == 1 && (previousStatus == null || previousStatus == 0)) {
                module.setCommandTime(new Date());
            }
        }

        // ✅ 모든 모듈에 대해 ON일 때 마지막 작동 시간 갱신
        if (status == 1) {
            module.setLastOperationTime(new Date());
        }

        // ✅ DB 저장
        moduleStatusRepository.save(module);

        // ✅ MQTT 전송
        String topic = "farm/" + DEVICE_ID + "/" + moduleName;
        String payload = "{\"command\":\"" + command + "\",\"status\":" + status + "}";
        mqttPublisherService.publish(topic, payload);

        logger.info("✅ [{}] 상태={}, 명령='{}' (MQTT 전송)", moduleName, status, command);
    }
}

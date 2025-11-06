package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.config.FarmSettings;
import com.smartfarm.smartfarm_server.service.FarmControlScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/module")
@RequiredArgsConstructor
public class ModuleControlController {

    private final FarmControlScheduler farmControlScheduler;

    /**
     * ✅ 수동 제어 (ON/OFF 버튼)
     * - 오버라이드 모드 5분간 자동제어 중지
     * - FarmSettings.MANUAL_OVERRIDE_DURATION_MINUTES 값 사용
     */
    @PostMapping("/control")
    public ResponseEntity<String> controlModule(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        String moduleName = request.get("moduleName");
        String command = request.get("command");

        if (moduleName == null || command == null) {
            return ResponseEntity.badRequest().body("❌ 모듈명 또는 명령이 없습니다.");
        }

        int status = command.equalsIgnoreCase("on") ? 1 : 0;

        // ✅ 오버라이드 5분(설정값) 동안 자동제어 비활성화
        int overrideMinutes = FarmSettings.MANUAL_OVERRIDE_DURATION_MINUTES;
        farmControlScheduler.enableManualOverride(moduleName, overrideMinutes);

        // ✅ 즉시 모듈 상태 업데이트 및 MQTT 전송
        farmControlScheduler.updateModuleStatus(moduleName, status, command, null);

        return ResponseEntity.ok(
                "✅ [" + moduleName + "] 명령: " + command.toUpperCase() +
                        " (오버라이드 " + overrideMinutes + "분 적용)"
        );
    }

    /**
     * ✅ 오버라이드 즉시 해제 버튼
     */
    @PostMapping("/override/disable")
    public ResponseEntity<String> disableOverride(@RequestBody Map<String, String> request) {
        String moduleName = request.get("moduleName");
        if (moduleName == null || moduleName.isEmpty()) {
            return ResponseEntity.badRequest().body("❌ 모듈명이 필요합니다.");
        }

        farmControlScheduler.disableManualOverride(moduleName);
        return ResponseEntity.ok("✅ [" + moduleName + "] 오버라이드 해제 완료");
    }
}

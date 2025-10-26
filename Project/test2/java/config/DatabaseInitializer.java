package com.smartfarm.smartfarm_server.config;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.model.ModuleStatus;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final CropInfoRepository cropInfoRepository;
    private final ModuleStatusRepository moduleStatusRepository; // 모듈 상태 리포지토리 추가

    public DatabaseInitializer(CropInfoRepository cropInfoRepository, ModuleStatusRepository moduleStatusRepository) {
        this.cropInfoRepository = cropInfoRepository;
        this.moduleStatusRepository = moduleStatusRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. CropInfo 초기화
        if (!cropInfoRepository.existsById("바질")) {
            CropInfo lettuceInfo = new CropInfo();
            lettuceInfo.setCrop("바질");
            lettuceInfo.setTargetTemp(26.0);
            lettuceInfo.setTargetHumi(70.0);
            lettuceInfo.setTargetSoil(40.0);
            lettuceInfo.setTargetLight(8.0);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_YEAR, 45);
            lettuceInfo.setHarvestDate(calendar.getTime());

            cropInfoRepository.save(lettuceInfo);
            System.out.println("기본 작물 정보 '바질'이 데이터베이스에 추가되었습니다. 🌱");
        }

        // 2. ModuleStatus 초기화
        if (moduleStatusRepository.count() == 0) {
            List<String> moduleNames = Arrays.asList(
                    "coolerA", "coolerB", "heater", "waterPump", "led"
            );

            List<ModuleStatus> initialData = moduleNames.stream()
                    .map(name -> {
                        ModuleStatus module = new ModuleStatus();
                        module.setModuleName(name);
                        module.setDeviceId("raspi-01");
                        module.setStatus(0);
                        module.setCommand("none");
                        module.setCommandTime(new Date());
                        module.setLastOperationTime(new Date());
                        module.setAccumulatedLightTime(0.0);
                        return module;
                    })
                    .toList();

            moduleStatusRepository.saveAll(initialData);
            System.out.println("✅ 초기 모듈 상태 5개 행이 성공적으로 추가되었습니다.");
        }
    }
}

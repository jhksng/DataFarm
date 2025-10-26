package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.ModuleStatus;
import com.smartfarm.smartfarm_server.repository.ModuleStatusRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
public class ModuleStatusController {

    private final ModuleStatusRepository moduleStatusRepository;

    // 생성자를 통해 Repository를 주입(DI) 받습니다.
    public ModuleStatusController(ModuleStatusRepository moduleStatusRepository) {
        this.moduleStatusRepository = moduleStatusRepository;
    }

    // '/module-status' URL로 GET 요청이 오면 이 메서드가 실행됩니다.
    @GetMapping("/module-status")
    public String showModuleStatus(Model model) {

        // 1. 데이터베이스에서 모든 ModuleStatus 레코드를 조회합니다.
        List<ModuleStatus> moduleStatuses = moduleStatusRepository.findAll();

        // 2. 조회된 리스트를 'statuses'라는 이름으로 모델에 담아 HTML로 전달합니다.
        model.addAttribute("statuses", moduleStatuses);

        // 3. 'moduleStatus.html' 템플릿을 찾아 사용자에게 보여줍니다.
        return "moduleStatus";
    }
}
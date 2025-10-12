package com.smartfarm.smartfarm_server.controller;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;
import java.util.Calendar;
import java.util.Optional;

@Controller
public class CropInfoController {
    @Autowired
    private CropInfoRepository cropInfoRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @GetMapping("/settings")
    public String showSettingsForm() {
        return "settings_form";
    }

    @PostMapping("/save_settings")
    public String saveSettings(
            @RequestParam String crop,
            @RequestParam Double targetTemp,
            @RequestParam Double targetHumi,
            @RequestParam Double targetSoil,
            @RequestParam Double targetLight,
            @RequestParam(required = false) Integer harvestDays) {

        CropInfo cropInfo = new CropInfo();
        cropInfo.setCrop(crop);
        cropInfo.setTargetTemp(targetTemp);
        cropInfo.setTargetHumi(targetHumi);
        cropInfo.setTargetSoil(targetSoil);
        cropInfo.setTargetLight(targetLight);
        // 새로운 작물을 추가할 때는 isActive를 false로 설정합니다.
        cropInfo.setActive(false);

        if (harvestDays != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_YEAR, harvestDays);
            cropInfo.setHarvestDate(calendar.getTime());
        }

        cropInfoRepository.save(cropInfo);

        return "redirect:/show_settings";
    }

    // 이 메서드는 URL 경로 변수로 작물 이름을 받아 활성화하는 역할을 합니다.
    @PostMapping("/activate_crop/{cropName}")
    public String activateCrop(@PathVariable String cropName, RedirectAttributes redirectAttributes) {
        // 1. 현재 활성화된 작물을 찾아서 비활성화합니다.
        Optional<CropInfo> currentActiveCropOptional = cropInfoRepository.findByIsActiveTrue();
        currentActiveCropOptional.ifPresent(activeCrop -> {
            activeCrop.setActive(false);
            cropInfoRepository.save(activeCrop);
        });

        // 2. 사용자가 선택한 작물을 찾아 활성화합니다.
        Optional<CropInfo> newActiveCropOptional = cropInfoRepository.findById(cropName);
        if (newActiveCropOptional.isPresent()) {
            CropInfo newActiveCrop = newActiveCropOptional.get();
            newActiveCrop.setActive(true);
            cropInfoRepository.save(newActiveCrop);
            redirectAttributes.addFlashAttribute("message", cropName + " 작물이 성공적으로 활성화되었습니다.");
        } else {
            redirectAttributes.addFlashAttribute("error", "해당 작물을 찾을 수 없습니다.");
        }

        return "redirect:/show_settings";
    }

    @GetMapping("/show_settings")
    public String showAllSettings(Model model) {
        List<CropInfo> crops = cropInfoRepository.findAll();
        model.addAttribute("crops", crops);
        return "show_settings";
    }

}
package com.smartfarm.smartfarm_server.controller; // 패키지 경로는 실제 환경에 맞게 확인하세요.

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp; // LocalDateTime -> Date 변환을 위해 import
import java.util.Date; // CropInfo 모델이 Date 타입을 사용하므로 import 유지
import java.util.List;
import java.util.Optional;

@Controller
public class CropInfoController {

    private final CropInfoRepository cropInfoRepository;

    @Autowired
    public CropInfoController(CropInfoRepository cropInfoRepository) {
        this.cropInfoRepository = cropInfoRepository;
    }

    @GetMapping("/settings")
    public String showSettingsForm(Model model) {
        CropInfo activeCrop = cropInfoRepository.findByIsActiveTrue()
                .orElse(new CropInfo());
        model.addAttribute("currentSettings", activeCrop);

        List<CropInfo> allCrops = cropInfoRepository.findAll();
        model.addAttribute("recommendedCrops", allCrops);

        return "settings_form"; // 템플릿 파일명 확인
    }

    @PostMapping("/save_settings")
    public String saveSettings(
            @RequestParam String crop,
            @RequestParam Double targetTemp,
            @RequestParam Double targetHumi,
            @RequestParam Double targetSoil,
            @RequestParam Double targetLight,
            @RequestParam(required = false, defaultValue = "0") Integer harvestDays,
            RedirectAttributes redirectAttributes
    ) {

        cropInfoRepository.deactivateAllCrops();

        CropInfo cropToSave = cropInfoRepository.findById(crop)
                .orElse(new CropInfo());

        cropToSave.setCrop(crop);
        cropToSave.setTargetTemp(targetTemp);
        cropToSave.setTargetHumi(targetHumi);
        cropToSave.setTargetSoil(targetSoil);
        cropToSave.setTargetLight(targetLight);

        // --- 여기만 수정 ---
        // 4. 예상 수확일 계산 및 Date 타입으로 변환
        if (harvestDays != null && harvestDays > 0) {
            LocalDateTime harvestDateTime = LocalDate.now().plusDays(harvestDays).atStartOfDay();
            // LocalDateTime을 java.util.Date로 변환
            Date harvestDate = Timestamp.valueOf(harvestDateTime); // Timestamp를 거쳐 Date로 변환
            cropToSave.setHarvestDate(harvestDate); // Date 타입으로 설정
        } else {
            cropToSave.setHarvestDate(null);
        }
        // --- 수정 끝 ---

        cropToSave.setActive(true);
        cropInfoRepository.save(cropToSave);
        redirectAttributes.addFlashAttribute("message", crop + " 작물 설정이 저장되고 활성화되었습니다. ✅");

        return "redirect:/show_settings";
    }

    @PostMapping("/activate_crop/{cropName}")
    public String activateCrop(@PathVariable String cropName, RedirectAttributes redirectAttributes) {
        cropInfoRepository.deactivateAllCrops();

        Optional<CropInfo> newActiveCropOptional = cropInfoRepository.findById(cropName);
        if (newActiveCropOptional.isPresent()) {
            CropInfo newActiveCrop = newActiveCropOptional.get();
            newActiveCrop.setActive(true);
            cropInfoRepository.save(newActiveCrop);
            redirectAttributes.addFlashAttribute("message", cropName + " 작물이 성공적으로 활성화되었습니다. 👍");
        } else {
            redirectAttributes.addFlashAttribute("error", "오류: '" + cropName + "' 작물을 찾을 수 없습니다. 😥");
        }
        return "redirect:/show_settings";
    }

    @GetMapping("/show_settings")
    public String showAllSettings(Model model) {
        List<CropInfo> crops = cropInfoRepository.findAll();
        cropInfoRepository.findByIsActiveTrue().ifPresent(activeCrop -> {
            model.addAttribute("activeCropName", activeCrop.getCrop());
        });
        model.addAttribute("crops", crops);
        return "show_settings";
    }
}
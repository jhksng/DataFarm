package com.smartfarm.smartfarm_server.service;

import com.smartfarm.smartfarm_server.model.CropInfo;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class CropInfoService {

    private final CropInfoRepository cropInfoRepository;

    public CropInfoService(CropInfoRepository cropInfoRepository) {
        this.cropInfoRepository = cropInfoRepository;
    }

    public Optional<CropInfo> getCropInfo(String cropName) {
        return cropInfoRepository.findById(cropName);
    }
}
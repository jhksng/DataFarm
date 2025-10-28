package com.smartfarm.smartfarm_server.service;

import com.smartfarm.smartfarm_server.model.Photo;
import com.smartfarm.smartfarm_server.repository.PhotoRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrowthAnalysisService {

    private final PhotoRepository photoRepository;

    public GrowthAnalysisService(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    public Map<String, Double> calculateDailyGrowthChange(String cropName) {
        Map<String, Double> result = new LinkedHashMap<>();
        final int DAILY_WINDOW = 24;

        List<Photo> last48 = photoRepository.findTop48ByCropNameOrderByUploadDateDesc(cropName);
        if (last48 == null || last48.size() < DAILY_WINDOW * 2) {
            result.put("yesterdayAvg", null);
            result.put("todayAvg", null);
            result.put("growthChangePercentage", null);
            return result;
        }

        List<Photo> sortedPhotos = last48.stream()
                .sorted(Comparator.comparing(Photo::getUploadDate))
                .collect(Collectors.toList());

        List<Double> values = sortedPhotos.stream()
                .map(Photo::getBrightnessRatio)
                .filter(Objects::nonNull)
                .map(Float::doubleValue)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                .collect(Collectors.toList());

        List<Double> prevDay = values.subList(values.size() - 48, values.size() - 24);
        List<Double> today = values.subList(values.size() - 24, values.size());

        double yAvg = prevDay.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double tAvg = today.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        Double delta = null;
        if (yAvg != 0.0) {
            delta = ((tAvg - yAvg) / yAvg) * 100.0;
            delta = Math.round(delta * 100.0) / 100.0;
        }

        result.put("yesterdayAvg", Math.round(yAvg * 100.0) / 100.0);
        result.put("todayAvg", Math.round(tAvg * 100.0) / 100.0);
        result.put("growthChangePercentage", delta);

        return result;
    }
}

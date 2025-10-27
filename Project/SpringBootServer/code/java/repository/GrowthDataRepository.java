package com.smartfarm.smartfarm_server.repository; // 패키지 경로 확인

import com.smartfarm.smartfarm_server.model.GrowthData; // GrowthData 모델 import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrowthDataRepository extends JpaRepository<GrowthData, Long> {

    /**
     * 특정 작물 이름에 해당하는 생장 데이터를 최대 10개까지 조회합니다.
     * timestamp 필드를 기준으로 오름차순(과거 -> 최신) 정렬합니다.
     * 대시보드 그래프의 X축 시간 순서대로 데이터를 가져오는 데 사용됩니다.
     *
     * @param cropName 조회할 작물의 이름
     * @return 조회된 GrowthData 객체 리스트 (최대 10개, 시간 오름차순)
     */
    List<GrowthData> findTop10ByCropNameOrderByTimestampAsc(String cropName);

    /**
     * [추가됨] 특정 작물 이름에 해당하는 생장 데이터를 최대 10개까지 조회합니다.
     * timestamp 필드를 기준으로 내림차순(최신 -> 과거) 정렬합니다.
     * 가장 최신 값과 그 이전 값을 쉽게 찾아서 변화율을 계산하는 데 사용됩니다.
     *
     * @param cropName 조회할 작물의 이름
     * @return 조회된 GrowthData 객체 리스트 (최대 10개, 시간 내림차순)
     */
    List<GrowthData> findTop10ByCropNameOrderByTimestampDesc(String cropName); // 이 메서드 추가

    // 필요하다면 다른 조회 메서드들을 추가할 수 있습니다.
    // 예: 특정 기간의 생장 데이터 조회 등
    // List<GrowthData> findByCropNameAndTimestampBetween(String cropName, LocalDateTime start, LocalDateTime end);
}
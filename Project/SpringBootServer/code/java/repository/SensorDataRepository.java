package com.smartfarm.smartfarm_server.repository; // 패키지 경로 확인

import com.smartfarm.smartfarm_server.model.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// import java.time.LocalDateTime; // 사용 안 함
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long>, JpaSpecificationExecutor<SensorData> {

    Optional<SensorData> findByRaspberryId(String raspberryId);
    Optional<SensorData> findTopByOrderByTimestampDesc();
    Page<SensorData> findAllBy(Pageable pageable);

    // 그래프를 위해 가장 최신 데이터 20개를 가져오는 메소드 (이것만 남김)
    List<SensorData> findTop20ByOrderByTimestampDesc();

    // findByTimestampBetweenOrderByTimestampAsc 메서드 제거
}
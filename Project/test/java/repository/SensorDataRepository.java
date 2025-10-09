package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;


@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    Optional<SensorData> findByRaspberryId(String raspberryId);

    Optional<SensorData> findTopByOrderByTimestampDesc();

    // 페이징 기능을 위해 Pageable을 사용하여 데이터를 가져옵니다.
    Page<SensorData> findAllBy(Pageable pageable);
}

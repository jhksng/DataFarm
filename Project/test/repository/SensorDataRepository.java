package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long>, JpaSpecificationExecutor<SensorData> {

    Optional<SensorData> findByRaspberryId(String raspberryId);
    Optional<SensorData> findTopByOrderByTimestampDesc();
    Page<SensorData> findAllBy(Pageable pageable);

    // 그래프를 위해 가장 최신 데이터 20개를 가져오는 메소드
    List<SensorData> findTop20ByOrderByTimestampDesc();
}
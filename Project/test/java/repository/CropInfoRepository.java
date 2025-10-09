package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.CropInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CropInfoRepository extends JpaRepository<CropInfo, String> {

    /**
     * isActive 상태가 true인 작물 정보를 찾습니다.
     * @return 활성화된 작물이 있다면 CropInfo 객체, 없다면 Optional.empty()
     */
    Optional<CropInfo> findByIsActiveTrue();
}

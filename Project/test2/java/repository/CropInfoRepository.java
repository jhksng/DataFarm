package com.smartfarm.smartfarm_server.repository; // 사용자 환경에 맞게 패키지 경로를 확인하세요.

import com.smartfarm.smartfarm_server.model.CropInfo; // 사용자 환경에 맞게 모델 경로를 확인하세요.
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface CropInfoRepository extends JpaRepository<CropInfo, String> { // 엔티티와 ID 타입 확인

    /**
     * isActive 상태가 true인 작물 정보를 찾습니다.
     * 스마트팜에서 현재 재배 중인 작물 설정을 가져올 때 사용됩니다.
     * @return 활성화된 작물이 있다면 CropInfo 객체, 없다면 Optional.empty()
     */
    Optional<CropInfo> findByIsActiveTrue();

    @Transactional // 데이터 변경 작업이므로 트랜잭션 처리
    @Modifying // SELECT 쿼리가 아닌, 데이터 변경(UPDATE, DELETE 등) 쿼리임을 명시
    @Query("UPDATE CropInfo c SET c.isActive = false") // JPQL 사용
    void deactivateAllCrops();
}
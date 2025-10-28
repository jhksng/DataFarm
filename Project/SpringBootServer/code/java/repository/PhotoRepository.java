package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    // 전체 URL로 조회
    Optional<Photo> findByPhotoUrl(String photoUrl);

    // 파일명 끝부분으로 조회 (prefix 상관없이)
    Optional<Photo> findByPhotoUrlEndingWith(String suffix);

    // 최근 48개 brightness_ratio 데이터 (시간 내림차순)
    List<Photo> findTop48ByCropNameOrderByUploadDateDesc(String cropName);

    Optional<Photo> findTopByPhotoUrlContainingOrderByUploadDateDesc(String partialName);
}

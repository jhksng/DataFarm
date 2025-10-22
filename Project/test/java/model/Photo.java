package com.smartfarm.smartfarm_server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import jakarta.persistence.Column;

@Entity
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500) // URL 저장을 위해 길이를 충분히 늘립니다.
    private String photoUrl; // 사진 URL을 저장할 필드

    private String cropName; // 활성화된 작물 이름을 저장할 필드

    private LocalDateTime uploadDate;

    // ✨ 새로 추가된 분석 수치값을 저장할 필드
    private Float brightnessRatio;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getCropName() {
        return cropName;
    }

    public void setCropName(String cropName) {
        this.cropName = cropName;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    // ✨ 새로 추가된 Getter
    public Float getBrightnessRatio() {
        return brightnessRatio;
    }

    // ✨ 새로 추가된 Setter
    public void setBrightnessRatio(Float brightnessRatio) {
        this.brightnessRatio = brightnessRatio;
    }

    public Photo() {}

    public Photo(String photoUrl, String cropName) {
        this.photoUrl = photoUrl;
        this.cropName = cropName;
        this.uploadDate = LocalDateTime.now();
    }
}

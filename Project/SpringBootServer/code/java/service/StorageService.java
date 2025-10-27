package com.smartfarm.smartfarm_server.service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StorageService {

    private final Storage storage;
    private final String bucketName = "datafarm-picture";

    // 캐시 구조
    private static class CachedUrl {
        URL url;
        Instant expireAt;

        CachedUrl(URL url, Instant expireAt) {
            this.url = url;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    private final Map<String, CachedUrl> cache = new ConcurrentHashMap<>();

    public StorageService() throws IOException {
        try {
            storage = StorageOptions.newBuilder()
                    .setCredentials(ServiceAccountCredentials.fromStream(
                            new ClassPathResource("neon-fort-471611-f2-a53def38e776.json").getInputStream()
                    ))
                    .build()
                    .getService();
            log.info("GCP Storage 서비스 계정 인증 성공.");
        } catch (IOException e) {
            log.error("GCP 서비스 계정 키 파일 로드 실패.", e);
            throw new IOException("GCP 서비스 계정 키 파일 로드 실패", e);
        }
    }

    /**
     * 10분 유효한 서명 URL 반환 (재사용 캐싱)
     */
    public URL generateSignedUrl(String filePath) throws IOException {
        String objectName = filePath.startsWith(bucketName + "/")
                ? filePath.substring(bucketName.length() + 1)
                : filePath;

        // 캐시 확인
        CachedUrl cached = cache.get(objectName);
        if (cached != null && !cached.isExpired()) {
            log.info("캐시된 URL 사용: {}", objectName);
            return cached.url;
        }

        // 새 URL 생성
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();
        try {
            log.info("GCS 객체 '{}' 서명 URL 생성 중...", objectName);
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    10, TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()
            );
            log.info("GCS 객체 '{}' 서명 URL 생성 성공: {}", objectName, signedUrl);

            // 캐시에 저장
            cache.put(objectName, new CachedUrl(signedUrl, Instant.now().plusSeconds(600)));
            return signedUrl;
        } catch (Exception e) {
            log.error("GCS 객체 '{}' 서명 URL 생성 실패.", objectName, e);
            throw new IOException("서명 URL 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
}

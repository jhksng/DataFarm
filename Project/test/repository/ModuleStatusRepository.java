package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.ModuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ModuleStatusRepository extends JpaRepository<ModuleStatus, Long> {
    Optional<ModuleStatus> findByDeviceIdAndModuleName(String deviceId, String moduleName);
}
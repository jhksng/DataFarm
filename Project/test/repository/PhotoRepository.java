package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
}

package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.setting.ServiceSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ServiceSettingRepository extends JpaRepository<ServiceSetting, Long> {
    Optional<ServiceSetting> findByKey(String key);

    @Modifying
    @Transactional
    @Query("UPDATE ServiceSetting s SET s.value = :value, s.updatedAt = CURRENT_TIMESTAMP WHERE s.key = :key")
    int updateValueByKey(@Param("key") String key, @Param("value") String value);
}

package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.setting.ServiceSetting;
import com.ldsilver.chingoohaja.dto.setting.OperatingHoursInfo;
import com.ldsilver.chingoohaja.repository.ServiceSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperatingHoursService {

    private final ServiceSettingRepository settingRepository;

    // 캐시 (1분마다 갱신)
    private volatile OperatingHoursInfo cachedInfo;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL = 60_000; // 1분

    @Transactional(readOnly = true)
    public OperatingHoursInfo getOperatingHoursInfo() {
        long now = System.currentTimeMillis();

        if (cachedInfo != null && (now - lastCacheTime) < CACHE_TTL) {
            return cachedInfo;
        }

        String enabled = settingRepository.findByKey("call_service_enabled")
                .map(ServiceSetting::getValue)
                .orElse("true");

        String startTime = settingRepository.findByKey("call_start_time")
                .map(ServiceSetting::getValue)
                .orElse("00:00");

        String endTime = settingRepository.findByKey("call_end_time")
                .map(ServiceSetting::getValue)
                .orElse("23:59");

        OperatingHoursInfo info = new OperatingHoursInfo(
                Boolean.parseBoolean(enabled),
                startTime,
                endTime
        );

        cachedInfo = info;
        lastCacheTime = System.currentTimeMillis();

        return info;
    }

    public boolean isOperatingTime() {
        OperatingHoursInfo info = getOperatingHoursInfo();

        if (!info.isEnabled()) {
            return false;
        }

        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        LocalTime start = LocalTime.parse(info.getStartTime());
        LocalTime end = LocalTime.parse(info.getEndTime());

        return !now.isBefore(start) && now.isBefore(end);
    }

    @Transactional
    public void updateOperatingHours(String startTime, String endTime) {
        settingRepository.updateValueByKey("call_start_time", startTime);
        settingRepository.updateValueByKey("call_end_time", endTime);

        // 캐시 무효화
        cachedInfo = null;
        log.info("Operating hours updated: {} - {}", startTime, endTime);
    }

    @Transactional
    public void toggleService(boolean enabled) {
        settingRepository.updateValueByKey("call_service_enabled", String.valueOf(enabled));

        // 캐시 무효화
        cachedInfo = null;
        log.info("Call service {}", enabled ? "enabled" : "disabled");
    }
}
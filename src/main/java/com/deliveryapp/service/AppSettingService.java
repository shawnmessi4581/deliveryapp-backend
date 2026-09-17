package com.deliveryapp.service;

import com.deliveryapp.dto.app.AppVersionResponse;
import com.deliveryapp.entity.AppSetting;
import com.deliveryapp.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private static final String APP_VERSION_KEY = "app_version";
    private static final String DEFAULT_VERSION = "1.0.0";

    private final AppSettingRepository appSettingRepository;

    public AppVersionResponse getAppVersion() {
        return appSettingRepository.findById(APP_VERSION_KEY)
                .map(s -> new AppVersionResponse(s.getValue(), s.getUpdatedAt()))
                .orElse(new AppVersionResponse(DEFAULT_VERSION, null));
    }

    public AppVersionResponse setAppVersion(String version) {
        AppSetting setting = appSettingRepository.findById(APP_VERSION_KEY)
                .orElse(new AppSetting(APP_VERSION_KEY, DEFAULT_VERSION, LocalDateTime.now()));

        setting.setValue(version);
        setting.setUpdatedAt(LocalDateTime.now());
        appSettingRepository.save(setting);

        return new AppVersionResponse(setting.getValue(), setting.getUpdatedAt());
    }
}

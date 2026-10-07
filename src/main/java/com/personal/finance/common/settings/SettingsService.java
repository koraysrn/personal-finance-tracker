package com.personal.finance.common.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Key/value store for user preferences such as the base currency, number
 * locale and date format. The application language stays English; only
 * formatting preferences are configurable.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> result = new LinkedHashMap<>();
        settingsRepository.findAll().forEach(setting -> result.put(setting.getKey(), setting.getValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return settingsRepository.findById(key).map(Setting::getValue);
    }

    @Transactional(readOnly = true)
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional
    public void put(String key, String value) {
        Setting setting = settingsRepository.findById(key).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value);
        settingsRepository.save(setting);
    }

    @Transactional
    public void putAll(Map<String, String> values) {
        values.forEach(this::put);
    }
}

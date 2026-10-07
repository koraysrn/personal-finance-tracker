package com.personal.finance.common.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Map<String, String> all() {
        return settingsService.all();
    }

    @PutMapping
    public Map<String, String> update(@RequestBody Map<String, String> values) {
        settingsService.putAll(values);
        return settingsService.all();
    }
}

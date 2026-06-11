package com.novelwriter.service;

import com.novelwriter.mapper.SettingsMapper;
import com.novelwriter.model.entity.SettingsEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM API 配置管理（api_key / base_url / model 仅从 MySQL nw_settings 读取）。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private final SettingsMapper settingsMapper;

    public LlmSettings getEffectiveSettings() {
        SettingsEntity entity = loadSettingsRow();
        LlmSettings settings = new LlmSettings();
        settings.setApiKey(nullToEmpty(entity.getApiKey()));
        settings.setBaseUrl(nullToEmpty(entity.getBaseUrl(), DEFAULT_BASE_URL));
        settings.setModel(nullToEmpty(entity.getModel(), DEFAULT_MODEL));
        return settings;
    }

    @Transactional
    public void updateSettings(String baseUrl, String model, String apiKey) {
        SettingsEntity entity = loadSettingsRow();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            entity.setBaseUrl(baseUrl);
        }
        if (model != null && !model.isEmpty()) {
            entity.setModel(model);
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            entity.setApiKey(apiKey);
        }
        settingsMapper.updateById(entity);
    }

    public String maskApiKey(String key) {
        if (key == null || key.length() < 8) {
            return "";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    private SettingsEntity loadSettingsRow() {
        SettingsEntity entity = settingsMapper.selectById(1);
        if (entity != null) {
            return entity;
        }
        entity = new SettingsEntity();
        entity.setId(1);
        entity.setApiKey("");
        entity.setBaseUrl(DEFAULT_BASE_URL);
        entity.setModel(DEFAULT_MODEL);
        settingsMapper.insert(entity);
        return entity;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String nullToEmpty(String value, String defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    @Data
    public static class LlmSettings {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}

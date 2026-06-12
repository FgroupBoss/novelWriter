package com.novelwriter.controller;

import com.novelwriter.config.StageConfigService;
import com.novelwriter.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConfigController {

    private final StageConfigService stageConfigService;
    private final SettingsService settingsService;

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("status", "ok");
        result.put("runtime", "springboot");
        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        SettingsService.LlmSettings llm = settingsService.getEffectiveSettings();

        Map<String, Object> api = new LinkedHashMap<String, Object>();
        api.put("model", llm.getModel());
        api.put("base_url", llm.getBaseUrl());
        api.put("api_key_set", llm.getApiKey() != null && !llm.getApiKey().isEmpty());
        api.put("api_key_masked", settingsService.maskApiKey(llm.getApiKey()));

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("default_novel", stageConfigService.getDefaultNovelConfig());
        result.put("api", api);
        result.put("setup_order", stageConfigService.getSetupOrder());
        result.put("stage_labels", StageConfigService.STAGE_LABELS);
        return result;
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, String> body) {
        String baseUrl = body.get("base_url");
        String model = body.get("model");
        String apiKey = body.get("api_key");
        if ((baseUrl == null || baseUrl.isEmpty())
                && (model == null || model.isEmpty())
                && (apiKey == null || apiKey.isEmpty())) {
            throw new IllegalArgumentException("无有效配置项");
        }
        settingsService.updateSettings(baseUrl, model, apiKey);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("message", "配置已保存");
        return result;
    }
}

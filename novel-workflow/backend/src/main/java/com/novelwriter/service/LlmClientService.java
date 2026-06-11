package com.novelwriter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelwriter.mapper.LlmLogMapper;
import com.novelwriter.model.entity.LlmLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 API 客户端，记录 token 用量与 prefix cache 命中情况。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClientService {

    private final SettingsService settingsService;
    private final LlmLogMapper llmLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${novel.llm.temperature:0.7}")
    private double temperature;

    @Value("${novel.llm.max-tokens:16384}")
    private int maxTokens;

    @Value("${novel.llm.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${novel.llm.retry-max:3}")
    private int retryMax;

    @Value("${novel.llm.retry-delay-seconds:5}")
    private int retryDelaySeconds;

    public LlmChatResult chat(String system, String user, String projectId, String stage, Integer chapter) {
        SettingsService.LlmSettings settings = settingsService.getEffectiveSettings();
        if (settings.getApiKey() == null || settings.getApiKey().isEmpty()) {
            throw new IllegalStateException(
                    "未设置 API Key。请在 Web 设置页保存 API Key（写入数据库后生效）。");
        }

        RestTemplate restTemplate = buildRestTemplate();
        String url = normalizeBaseUrl(settings.getBaseUrl()) + "/chat/completions";

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("model", settings.getModel());
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> sysMsg = new HashMap<String, String>();
        sysMsg.put("role", "system");
        sysMsg.put("content", system);
        messages.add(sysMsg);
        Map<String, String> userMsg = new HashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", user);
        messages.add(userMsg);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(settings.getApiKey());

        Exception lastErr = null;
        for (int attempt = 1; attempt <= retryMax; attempt++) {
            try {
                log.info("调用 LLM model={} attempt={}/{}", settings.getModel(), attempt, retryMax);
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        url, new HttpEntity<String>(objectMapper.writeValueAsString(body), headers), String.class);

                JsonNode root = objectMapper.readTree(resp.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                if (content.trim().isEmpty()) {
                    throw new RuntimeException("模型返回空内容");
                }

                LlmChatResult result = new LlmChatResult(content);
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    result.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                    result.setCompletionTokens(usage.path("completion_tokens").asInt(0));
                    result.setTotalTokens(usage.path("total_tokens").asInt(0));
                    if (usage.has("prompt_cache_hit_tokens")) {
                        result.setCacheHitTokens(usage.path("prompt_cache_hit_tokens").asInt(0));
                    }
                    if (usage.has("prompt_cache_miss_tokens")) {
                        result.setCacheMissTokens(usage.path("prompt_cache_miss_tokens").asInt(0));
                    }

                    if (result.getCacheHitTokens() != null || result.getCacheMissTokens() != null) {
                        log.info("Token usage: prompt={} completion={} total={} cache_hit={} cache_miss={}",
                                result.getPromptTokens(), result.getCompletionTokens(), result.getTotalTokens(),
                                result.getCacheHitTokens(), result.getCacheMissTokens());
                    } else {
                        log.info("Token usage: prompt={} completion={} total={}",
                                result.getPromptTokens(), result.getCompletionTokens(), result.getTotalTokens());
                    }
                }

                saveLlmLog(projectId, stage, chapter, result, content);
                return result;
            } catch (Exception e) {
                lastErr = e;
                log.warn("LLM 调用失败 attempt={}: {}", attempt, e.getMessage());
                if (attempt < retryMax) {
                    try {
                        Thread.sleep(retryDelaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM 调用被中断", ie);
                    }
                }
            }
        }
        throw new RuntimeException("LLM 调用失败（已重试 " + retryMax + " 次）: " + lastErr.getMessage(), lastErr);
    }

    private void saveLlmLog(String projectId, String stage, Integer chapter, LlmChatResult result, String raw) {
        LlmLogEntity entity = new LlmLogEntity();
        entity.setProjectId(projectId);
        entity.setStage(stage);
        entity.setChapter(chapter);
        entity.setPromptTokens(result.getPromptTokens());
        entity.setCompletionTokens(result.getCompletionTokens());
        entity.setTotalTokens(result.getTotalTokens());
        entity.setCacheHitTokens(result.getCacheHitTokens());
        entity.setCacheMissTokens(result.getCacheMissTokens());
        entity.setRawResponse(raw);
        llmLogMapper.insert(entity);
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(factory);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static class LlmChatResult {
        private final String content;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Integer cacheHitTokens;
        private Integer cacheMissTokens;

        public LlmChatResult(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Integer getCacheHitTokens() {
            return cacheHitTokens;
        }

        public void setCacheHitTokens(Integer cacheHitTokens) {
            this.cacheHitTokens = cacheHitTokens;
        }

        public Integer getCacheMissTokens() {
            return cacheMissTokens;
        }

        public void setCacheMissTokens(Integer cacheMissTokens) {
            this.cacheMissTokens = cacheMissTokens;
        }
    }
}

package com.novelwriter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novelwriter.mapper.PromptRecordMapper;
import com.novelwriter.model.entity.PromptRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptRecordService {

    private final PromptRecordMapper promptRecordMapper;

    @Transactional
    public void save(String projectId, String jobId, String stageId, Integer chapter,
                     String promptType, String relPath, String content) {
        PromptRecordEntity entity = new PromptRecordEntity();
        entity.setProjectId(projectId);
        entity.setJobId(jobId);
        entity.setStageId(stageId);
        entity.setChapter(chapter);
        entity.setPromptType(promptType);
        entity.setRelPath(relPath);
        entity.setContent(content);
        entity.setCharCount(content != null ? content.length() : 0);
        promptRecordMapper.insert(entity);
    }

    @Transactional
    public void seedProjectTemplates(String projectId) throws IOException {
        long count = promptRecordMapper.selectCount(new LambdaQueryWrapper<PromptRecordEntity>()
                .eq(PromptRecordEntity::getProjectId, projectId)
                .eq(PromptRecordEntity::getPromptType, "template"));
        if (count > 0) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:prompts/*.md");
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            String filename = resource.getFilename();
            byte[] bytes = org.springframework.util.StreamUtils.copyToByteArray(resource.getInputStream());
            save(projectId, null, null, null, "template", filename,
                    new String(bytes, StandardCharsets.UTF_8));
        }
    }

    public List<Map<String, Object>> listByProject(String projectId, String promptType, String jobId) {
        LambdaQueryWrapper<PromptRecordEntity> q = new LambdaQueryWrapper<PromptRecordEntity>()
                .eq(PromptRecordEntity::getProjectId, projectId)
                .orderByDesc(PromptRecordEntity::getCreatedAt);
        if (promptType != null && !promptType.isEmpty()) {
            q.eq(PromptRecordEntity::getPromptType, promptType);
        }
        if (jobId != null && !jobId.isEmpty()) {
            q.eq(PromptRecordEntity::getJobId, jobId);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (PromptRecordEntity e : promptRecordMapper.selectList(q)) {
            result.add(toMap(e, false));
        }
        return result;
    }

    public Map<String, Object> getDetail(Long id) {
        PromptRecordEntity e = promptRecordMapper.selectById(id);
        if (e == null) {
            return null;
        }
        return toMap(e, true);
    }

    private Map<String, Object> toMap(PromptRecordEntity e, boolean includeContent) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId());
        m.put("project_id", e.getProjectId());
        m.put("job_id", e.getJobId());
        m.put("stage_id", e.getStageId());
        m.put("chapter", e.getChapter());
        m.put("prompt_type", e.getPromptType());
        m.put("rel_path", e.getRelPath());
        m.put("char_count", e.getCharCount());
        m.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        if (includeContent) {
            m.put("content", e.getContent());
        } else {
            String preview = e.getContent();
            if (preview != null && preview.length() > 120) {
                preview = preview.substring(0, 120) + "…";
            }
            m.put("preview", preview);
        }
        return m;
    }
}

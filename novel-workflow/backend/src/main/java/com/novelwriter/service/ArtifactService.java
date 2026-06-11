package com.novelwriter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novelwriter.mapper.DocumentMapper;
import com.novelwriter.model.entity.DocumentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final DocumentMapper documentMapper;

    public List<Map<String, Object>> listArtifacts(String projectId, String category) {
        LambdaQueryWrapper<DocumentEntity> q = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId)
                .orderByAsc(DocumentEntity::getCategory)
                .orderByAsc(DocumentEntity::getRelPath);
        if (category != null && !category.isEmpty() && !"全部".equals(category)) {
            q.eq(DocumentEntity::getCategory, category);
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocumentEntity doc : documentMapper.selectList(q)) {
            items.add(toSummary(doc));
        }
        return items;
    }

    public Map<String, Object> getArtifactStats(String projectId) {
        List<DocumentEntity> docs = documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId));
        Map<String, Integer> byCategory = new LinkedHashMap<String, Integer>();
        for (String label : ArtifactClassifier.categoryLabels().keySet()) {
            byCategory.put(label, 0);
        }
        for (DocumentEntity doc : docs) {
            String cat = doc.getCategory() != null ? doc.getCategory() : "其他";
            byCategory.put(cat, byCategory.getOrDefault(cat, 0) + 1);
        }
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        stats.put("total", docs.size());
        stats.put("by_category", byCategory);
        stats.put("categories", ArtifactClassifier.categoryLabels());
        return stats;
    }

    public Map<String, Object> getArtifact(String projectId, String relPath) {
        DocumentEntity doc = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId)
                .eq(DocumentEntity::getRelPath, relPath.replace("\\", "/")));
        if (doc == null) {
            return null;
        }
        return toDetail(doc);
    }

    private Map<String, Object> toSummary(DocumentEntity doc) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", doc.getId());
        m.put("path", doc.getRelPath());
        m.put("name", doc.getRelPath().contains("/")
                ? doc.getRelPath().substring(doc.getRelPath().lastIndexOf('/') + 1) : doc.getRelPath());
        m.put("artifact_type", doc.getArtifactType());
        m.put("category", doc.getCategory());
        m.put("stage_id", doc.getStageId());
        m.put("chapter", doc.getChapter());
        m.put("source_type", doc.getSourceType());
        m.put("job_id", doc.getJobId());
        m.put("char_count", doc.getContent() != null ? doc.getContent().length() : 0);
        m.put("updated_at", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toDetail(DocumentEntity doc) {
        Map<String, Object> m = toSummary(doc);
        m.put("content", doc.getContent());
        return m;
    }
}

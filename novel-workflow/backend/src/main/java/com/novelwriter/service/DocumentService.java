package com.novelwriter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novelwriter.common.AssistException;
import com.novelwriter.mapper.DocumentMapper;
import com.novelwriter.model.entity.DocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 项目文档读写（MySQL 持久化，路径结构与文件系统版一致）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final DocumentMapper documentMapper;

    public void validateProjectId(String projectId) {
        if (projectId == null || projectId.startsWith("_") || !PROJECT_ID_PATTERN.matcher(projectId).matches()) {
            throw new AssistException("项目 ID 仅允许字母、数字、下划线、连字符，且不能以 _ 开头");
        }
    }

    public String safeRelPath(String relPath) {
        if (relPath == null) {
            throw new AssistException("非法路径");
        }
        String rel = relPath.replace("\\", "/").replaceAll("^/+", "");
        for (String part : rel.split("/")) {
            if ("..".equals(part)) {
                throw new AssistException("非法路径");
            }
        }
        return rel;
    }

    public String readContent(String projectId, String relPath) {
        String rel = safeRelPath(relPath);
        DocumentEntity doc = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId)
                .eq(DocumentEntity::getRelPath, rel));
        return doc == null ? null : doc.getContent();
    }

    /**
     * 读取项目文件，过滤占位内容。
     */
    public String readProjectFile(String projectId, String relPath) {
        String text = readContent(projectId, relPath);
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty() || isPlaceholderContent(text)) {
            return null;
        }
        return text;
    }

    @Transactional
    public void writeFile(String projectId, String relPath, String content) {
        writeFile(projectId, relPath, content, null);
    }

    @Transactional
    public void writeFile(String projectId, String relPath, String content, DocumentWriteMeta meta) {
        String rel = safeRelPath(relPath);
        ArtifactClassifier.Meta classified = ArtifactClassifier.classify(rel);
        DocumentEntity existing = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId)
                .eq(DocumentEntity::getRelPath, rel));
        if (existing != null) {
            existing.setContent(content);
            applyMeta(existing, classified, meta);
            documentMapper.updateById(existing);
        } else {
            DocumentEntity doc = new DocumentEntity();
            doc.setProjectId(projectId);
            doc.setRelPath(rel);
            doc.setContent(content);
            applyMeta(doc, classified, meta);
            documentMapper.insert(doc);
        }
    }

    private void applyMeta(DocumentEntity doc, ArtifactClassifier.Meta classified, DocumentWriteMeta meta) {
        doc.setArtifactType(classified.getArtifactType());
        doc.setCategory(classified.getCategory());
        if (meta != null) {
            if (meta.getStageId() != null) {
                doc.setStageId(meta.getStageId());
            }
            if (meta.getChapter() != null) {
                doc.setChapter(meta.getChapter());
            }
            if (meta.getSourceType() != null) {
                doc.setSourceType(meta.getSourceType());
            }
            if (meta.getJobId() != null) {
                doc.setJobId(meta.getJobId());
            }
        }
        if (doc.getSourceType() == null) {
            doc.setSourceType("manual");
        }
    }

    @Transactional
    public void backfillMetadata(String projectId) {
        for (DocumentEntity doc : listByProject(projectId)) {
            ArtifactClassifier.Meta classified = ArtifactClassifier.classify(doc.getRelPath());
            doc.setArtifactType(classified.getArtifactType());
            doc.setCategory(classified.getCategory());
            if (doc.getSourceType() == null || doc.getSourceType().isEmpty()) {
                doc.setSourceType("import");
            }
            documentMapper.updateById(doc);
        }
    }

    /**
     * 若文档缺失则从 classpath 模板补种（修复 JAR 内扫描漏文件导致 context_index 等缺失）。
     */
    @Transactional
    public void ensureBootstrapDocument(String projectId, String relPath, String classpathTemplate) {
        if (readContent(projectId, relPath) != null) {
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource(classpathTemplate);
            if (!resource.exists()) {
                throw new AssistException("模板不存在: " + classpathTemplate);
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            content = content.replace("_template", projectId);
            writeFile(projectId, relPath, content, DocumentWriteMeta.builder().sourceType("template").build());
            log.info("已补种缺失文档 projectId={} path={}", projectId, relPath);
        } catch (IOException e) {
            throw new AssistException("补种模板文件失败: " + relPath + " — " + e.getMessage(), e);
        }
    }

    /** 补种 context_index 等关键 meta 文件。 */
    @Transactional
    public void ensureProjectBootstrapFiles(String projectId) {
        ensureBootstrapDocument(projectId, "meta/context_index.md", "_template/meta/context_index.md");
    }

    public List<DocumentEntity> listByProject(String projectId) {
        return documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId)
                .orderByAsc(DocumentEntity::getRelPath));
    }

    public List<String> listFilePaths(String projectId) {
        List<DocumentEntity> docs = listByProject(projectId);
        List<String> paths = new ArrayList<String>();
        for (DocumentEntity doc : docs) {
            paths.add(doc.getRelPath());
        }
        return paths;
    }

    @Transactional
    public void copyDocuments(String fromProjectId, String toProjectId) {
        for (DocumentEntity doc : listByProject(fromProjectId)) {
            DocumentWriteMeta meta = DocumentWriteMeta.builder()
                    .sourceType(doc.getSourceType() != null ? doc.getSourceType() : "template")
                    .stageId(doc.getStageId())
                    .chapter(doc.getChapter())
                    .build();
            writeFile(toProjectId, doc.getRelPath(), doc.getContent(), meta);
        }
    }

    @Transactional
    public void deleteByProject(String projectId) {
        documentMapper.delete(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getProjectId, projectId));
    }

    public boolean isPlaceholderContent(String text) {
        List<String> substantive = new ArrayList<String>();
        for (String line : text.split("\n")) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith(">") || s.startsWith("<!--")) {
                continue;
            }
            substantive.add(s);
        }
        if (substantive.isEmpty()) {
            return true;
        }
        String joined = String.join(" ", substantive);
        if ("（待生成）".equals(joined) || "(待生成)".equals(joined)) {
            return true;
        }
        return joined.length() <= 20 && joined.contains("待生成");
    }

    /**
     * 构建文件树（与 Node list files API 对齐）。
     */
    public List<java.util.Map<String, String>> buildFileTree(String projectId) {
        List<String> paths = listFilePaths(projectId);
        java.util.Set<String> dirs = new java.util.LinkedHashSet<String>();
        List<java.util.Map<String, String>> nodes = new ArrayList<java.util.Map<String, String>>();

        for (String path : paths) {
            if (!path.matches(".*\\.(md|json|yaml|yml)$")) {
                continue;
            }
            String[] parts = path.split("/");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (prefix.length() > 0) {
                    prefix.append("/");
                }
                prefix.append(parts[i]);
                dirs.add(prefix.toString());
            }
        }

        List<String> allPaths = new ArrayList<String>(dirs);
        allPaths.addAll(paths);
        allPaths.sort(Comparator.naturalOrder());

        java.util.Set<String> added = new java.util.HashSet<String>();
        for (String p : allPaths) {
            if (added.contains(p)) {
                continue;
            }
            added.add(p);
            java.util.Map<String, String> node = new java.util.LinkedHashMap<String, String>();
            node.put("path", p);
            node.put("name", p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p);
            node.put("type", dirs.contains(p) ? "dir" : "file");
            if ("file".equals(node.get("type")) && p.matches(".*\\.(md|json|yaml|yml)$")) {
                nodes.add(node);
            } else if ("dir".equals(node.get("type"))) {
                nodes.add(node);
            }
        }
        return nodes;
    }
}

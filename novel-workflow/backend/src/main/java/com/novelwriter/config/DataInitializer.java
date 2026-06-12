package com.novelwriter.config;

import com.novelwriter.mapper.ProjectMapper;
import com.novelwriter.service.DocumentService;
import com.novelwriter.service.PromptRecordService;
import com.novelwriter.service.NovelConfigService;
import com.novelwriter.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 首次启动时从文件系统导入 demo_novel（若 DB 为空且目录存在）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final DocumentService documentService;
    private final PromptRecordService promptRecordService;
    private final NovelConfigService novelConfigService;

    @Value("${novel.import-demo-path:}")
    private String importDemoPath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        backfillAllProjects();

        if (projectMapper.selectCount(null) > 0) {
            log.info("数据库已有项目，跳过 demo 导入");
            return;
        }

        String demoPath = resolveDemoPath();
        if (demoPath != null) {
            File demoDir = new File(demoPath);
            if (demoDir.isDirectory()) {
                log.info("导入 demo 项目: {}", demoDir.getAbsolutePath());
                projectService.importFromFilesystem("demo_novel", demoDir);
                log.info("demo_novel 导入完成");
                return;
            }
        }

        log.info("无 demo 目录，创建示例项目 demo_novel");
        projectService.createProject("demo_novel", "示例小说");
    }

    private String resolveDemoPath() {
        if (importDemoPath != null && !importDemoPath.isEmpty()) {
            return importDemoPath;
        }
        File relative = new File("../projects/demo_novel");
        if (relative.isDirectory()) {
            return relative.getAbsolutePath();
        }
        File sibling = new File("projects/demo_novel");
        if (sibling.isDirectory()) {
            return sibling.getAbsolutePath();
        }
        return null;
    }

    private void backfillAllProjects() {
        try {
            novelConfigService.backfillAllProjects();
            for (com.novelwriter.model.entity.ProjectEntity p : projectMapper.selectList(null)) {
                if (p.getId().startsWith("_")) {
                    continue;
                }
                documentService.backfillMetadata(p.getId());
                documentService.ensureProjectBootstrapFiles(p.getId());
                promptRecordService.seedProjectTemplates(p.getId());
            }
        } catch (Exception e) {
            log.warn("产物/Prompt 元数据回填失败", e);
        }
    }
}

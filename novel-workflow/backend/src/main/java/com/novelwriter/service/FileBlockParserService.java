package com.novelwriter.service;

import com.novelwriter.common.AssistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 ===FILE:path=== ... ===END=== 格式的模型输出。
 * 与 Python parser.py 对齐，含多级降级策略。
 */
@Slf4j
@Service
public class FileBlockParserService {

    private static final Pattern FILE_BLOCK_RE = Pattern.compile(
            "===FILE:\\s*(?<path>[^\\s=]+?)\\s*===\\s*\\n(?<content>.*?)(?=\\n===FILE:|\\n===END===|\\Z)",
            Pattern.DOTALL);
    private static final Pattern FILE_BLOCK_LOOSE_RE = Pattern.compile(
            "===FILE:\\s*(?<path>[^\\s=]+?)\\s*===\\s*\\n(?<content>.*?)(?=\\n===FILE:|\\Z)",
            Pattern.DOTALL);
    private static final Pattern END_SUFFIX_RE = Pattern.compile("\\n===END===\\s*$");

    private static final Map<String, String> HEADING_HINTS = new LinkedHashMap<String, String>();

    static {
        HEADING_HINTS.put("10_plot_progress.md", "^#\\s*剧情推进记录");
        HEADING_HINTS.put("07_foreshadowing.md", "^#\\s*伏笔");
        HEADING_HINTS.put("12_timeline.md", "^#\\s*时间线");
    }

    public Map<String, String> parseFileBlocks(String raw, List<String> expected) {
        List<String> expectedNorm = normalizePaths(expected);

        Map<String, String> strict = extractFileBlocks(raw, FILE_BLOCK_RE);
        if (!strict.isEmpty()) {
            return strict;
        }

        Map<String, String> loose = extractFileBlocks(raw, FILE_BLOCK_LOOSE_RE);
        if (!loose.isEmpty()) {
            log.warn("使用宽松 FILE 块解析（缺少 ===END===）paths={}", loose.keySet());
            return loose;
        }

        Map<String, String> alt = parseMarkdownPathBlocks(raw);
        if (!alt.isEmpty() && coversExpected(alt, expectedNorm)) {
            log.warn("使用 markdown 代码块路径降级解析 paths={}", alt.keySet());
            return alt;
        }

        if (expectedNorm != null && !expectedNorm.isEmpty()) {
            Map<String, String> single = parseSingleFileFallback(raw, expectedNorm);
            if (single != null) {
                log.warn("使用单文件整段降级解析 path={}", expectedNorm.get(0));
                return single;
            }

            Map<String, String> multi = parseMultiFileByHeadings(raw, expectedNorm);
            if (multi != null) {
                log.warn("使用多文件标题分段降级解析 paths={}", multi.keySet());
                return multi;
            }
        }

        throw new AssistException(
                "无法解析模型输出。期望格式:\n"
                        + "===FILE:相对路径===\n内容\n===END===\n"
                        + "请检查模型是否遵循输出格式，或重试。");
    }

    public ParseResult validateOutputs(List<String> expected, Map<String, String> parsed) {
        Map<String, String> normalized = new HashMap<String, String>();
        for (Map.Entry<String, String> e : parsed.entrySet()) {
            normalized.put(e.getKey().replace("\\", "/"), e.getValue());
        }

        Map<String, String> out = new LinkedHashMap<String, String>();
        List<String> missing = new ArrayList<String>();

        for (String exp : expected) {
            String key = exp.replace("\\", "/");
            if (normalized.containsKey(key)) {
                out.put(key, normalized.get(key));
            } else {
                String matched = fuzzyMatch(key, normalized);
                if (matched != null) {
                    out.put(key, normalized.get(matched));
                } else {
                    missing.add(exp);
                }
            }
        }
        return new ParseResult(out, missing);
    }

    private Map<String, String> extractFileBlocks(String raw, Pattern pattern) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        Matcher m = pattern.matcher(raw);
        while (m.find()) {
            String path = m.group("path").trim().replace("\\", "/");
            String content = END_SUFFIX_RE.matcher(m.group("content")).replaceAll("").trim();
            if (!path.isEmpty()) {
                files.put(path, content);
            }
        }
        return files;
    }

    private boolean coversExpected(Map<String, String> parsed, List<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return !parsed.isEmpty();
        }
        Map<String, String> normalized = new HashMap<String, String>();
        for (Map.Entry<String, String> e : parsed.entrySet()) {
            normalized.put(e.getKey().replace("\\", "/"), e.getValue());
        }
        for (String exp : expected) {
            if (normalized.containsKey(exp)) {
                continue;
            }
            if (fuzzyMatch(exp, normalized) == null) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> parseSingleFileFallback(String raw, List<String> expected) {
        if (expected.size() != 1) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || text.contains("===FILE:")) {
            return null;
        }
        String path = expected.get(0);
        if (path.endsWith("_review.md") && (text.contains("校验报告") || text.matches("^#\\s*第\\s*\\d+\\s*章.*"))) {
            Map<String, String> result = new LinkedHashMap<String, String>();
            result.put(path, text);
            return result;
        }
        if (text.length() > 200) {
            Map<String, String> result = new LinkedHashMap<String, String>();
            result.put(path, text);
            return result;
        }
        return null;
    }

    private Map<String, String> parseMultiFileByHeadings(String raw, List<String> expected) {
        if (expected.size() < 2 || raw.contains("===FILE:")) {
            return null;
        }
        List<String[]> hints = new ArrayList<String[]>();
        for (String p : expected) {
            if (HEADING_HINTS.containsKey(p)) {
                hints.add(new String[]{p, HEADING_HINTS.get(p)});
            }
        }
        if (hints.size() < 2) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        String[] sections = text.split("(?=^#\\s)", Pattern.MULTILINE);
        List<String> sectionList = new ArrayList<String>();
        for (String s : sections) {
            if (!s.trim().isEmpty()) {
                sectionList.add(s.trim());
            }
        }
        if (sectionList.size() < hints.size()) {
            return null;
        }

        Map<String, String> result = new LinkedHashMap<String, String>();
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        for (String[] hint : hints) {
            String path = hint[0];
            Pattern hintPattern = Pattern.compile(hint[1], Pattern.MULTILINE);
            for (int i = 0; i < sectionList.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                if (hintPattern.matcher(sectionList.get(i)).find()) {
                    result.put(path, sectionList.get(i));
                    used.add(i);
                    break;
                }
            }
        }
        return result.size() == hints.size() ? result : null;
    }

    private Map<String, String> parseMarkdownPathBlocks(String raw) {
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("```(?:markdown|md)?\\s*(?<path>[\\w./_-]+\\.md)\\s*\\n(?<content>.*?)```", Pattern.DOTALL),
                Pattern.compile("```(?:markdown|md)?\\s*\\n(?<path>[\\w./_-]+\\.md)\\s*\\n(?<content>.*?)```", Pattern.DOTALL)
        };
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(raw);
            while (m.find()) {
                result.put(m.group("path").trim(), m.group("content").trim());
            }
        }
        return result;
    }

    private String fuzzyMatch(String expected, Map<String, String> parsed) {
        String expName = expected.contains("/") ? expected.substring(expected.lastIndexOf('/') + 1) : expected;
        for (String p : parsed.keySet()) {
            if (p.endsWith(expName) || p.substring(p.lastIndexOf('/') + 1).equals(expName)) {
                return p;
            }
        }
        return null;
    }

    private List<String> normalizePaths(List<String> expected) {
        if (expected == null) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>();
        for (String p : expected) {
            result.add(p.replace("\\", "/"));
        }
        return result;
    }

    public static class ParseResult {
        private final Map<String, String> validated;
        private final List<String> missing;

        public ParseResult(Map<String, String> validated, List<String> missing) {
            this.validated = validated;
            this.missing = missing;
        }

        public Map<String, String> getValidated() {
            return validated;
        }

        public List<String> getMissing() {
            return missing;
        }
    }
}

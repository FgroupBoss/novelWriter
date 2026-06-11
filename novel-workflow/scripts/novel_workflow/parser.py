"""解析 ===FILE:path=== ... ===END=== 格式的模型输出。"""

from __future__ import annotations

import logging
import re
from typing import Dict, List, Optional, Tuple

log = logging.getLogger(__name__)

FILE_BLOCK_RE = re.compile(
    r"===FILE:\s*(?P<path>[^\s=]+?)\s*===\s*\n(?P<content>.*?)(?=\n===FILE:|\n===END===|\Z)",
    re.DOTALL,
)
# 模型常省略末尾 ===END===
FILE_BLOCK_LOOSE_RE = re.compile(
    r"===FILE:\s*(?P<path>[^\s=]+?)\s*===\s*\n(?P<content>.*?)(?=\n===FILE:|\Z)",
    re.DOTALL,
)
END_SUFFIX_RE = re.compile(r"\n===END===\s*$")


class ParseError(Exception):
    pass


def parse_file_blocks(raw: str, expected: Optional[List[str]] = None) -> Dict[str, str]:
    """从模型回复中提取 path -> content。"""
    expected_norm = [p.replace("\\", "/") for p in (expected or [])]

    for name, pattern in (("strict", FILE_BLOCK_RE), ("loose", FILE_BLOCK_LOOSE_RE)):
        files = _extract_file_blocks(raw, pattern)
        if files:
            if name == "loose":
                log.warning("使用宽松 FILE 块解析（缺少 ===END===）paths=%s", list(files.keys()))
            return files

    alt = _parse_markdown_path_blocks(raw)
    if alt and _covers_expected(alt, expected_norm):
        log.warning("使用 markdown 代码块路径降级解析 paths=%s", list(alt.keys()))
        return alt

    if expected_norm:
        single = _parse_single_file_fallback(raw, expected_norm)
        if single:
            log.warning("使用单文件整段降级解析 path=%s", expected_norm[0])
            return single

        multi = _parse_multi_file_by_headings(raw, expected_norm)
        if multi:
            log.warning("使用多文件标题分段降级解析 paths=%s", list(multi.keys()))
            return multi

    raise ParseError(
        "无法解析模型输出。期望格式:\n"
        "===FILE:相对路径===\n内容\n===END===\n"
        "请检查模型是否遵循输出格式，或重试。"
    )


def _extract_file_blocks(raw: str, pattern: re.Pattern) -> Dict[str, str]:
    files: Dict[str, str] = {}
    for m in pattern.finditer(raw):
        path = m.group("path").strip().replace("\\", "/")
        content = END_SUFFIX_RE.sub("", m.group("content")).strip()
        if path:
            files[path] = content
    return files


def _covers_expected(parsed: Dict[str, str], expected: List[str]) -> bool:
    if not expected:
        return bool(parsed)
    normalized = {k.replace("\\", "/"): v for k, v in parsed.items()}
    for exp in expected:
        if exp in normalized:
            continue
        if _fuzzy_match(exp, normalized) is None:
            return False
    return True


def _parse_single_file_fallback(raw: str, expected: List[str]) -> Optional[Dict[str, str]]:
    """模型未包 FILE 块时，对单文件阶段整段落盘。"""
    if len(expected) != 1:
        return None
    text = raw.strip()
    if not text or "===FILE:" in text:
        return None
    path = expected[0]
    if path.endswith("_review.md") and ("校验报告" in text or re.match(r"^#\s*第\s*\d+\s*章", text)):
        return {path: text}
    if len(text) > 200:
        return {path: text}
    return None


# 多文件阶段：按各文件典型一级标题分段（plot_update 等）
_HEADING_HINTS: Dict[str, str] = {
    "10_plot_progress.md": r"^#\s*剧情推进记录",
    "07_foreshadowing.md": r"^#\s*伏笔",
    "12_timeline.md": r"^#\s*时间线",
}


def _parse_multi_file_by_headings(raw: str, expected: List[str]) -> Optional[Dict[str, str]]:
    if len(expected) < 2 or "===FILE:" in raw:
        return None
    hints = [(p, _HEADING_HINTS[p]) for p in expected if p in _HEADING_HINTS]
    if len(hints) < 2:
        return None
    text = raw.strip()
    if not text:
        return None
    # 按一级标题切分
    sections = re.split(r"(?=^#\s)", text, flags=re.MULTILINE)
    sections = [s.strip() for s in sections if s.strip()]
    if len(sections) < len(hints):
        return None
    result: Dict[str, str] = {}
    used: set = set()
    for path, hint in hints:
        for i, sec in enumerate(sections):
            if i in used:
                continue
            if re.match(hint, sec, re.MULTILINE):
                result[path] = sec
                used.add(i)
                break
    if len(result) == len(hints):
        return result
    return None


def _parse_markdown_path_blocks(raw: str) -> Dict[str, str]:
    patterns = [
        re.compile(
            r"```(?:markdown|md)?\s*(?P<path>[\w./_-]+\.md)\s*\n(?P<content>.*?)```",
            re.DOTALL,
        ),
        re.compile(
            r"```(?:markdown|md)?\s*\n(?P<path>[\w./_-]+\.md)\s*\n(?P<content>.*?)```",
            re.DOTALL,
        ),
    ]
    result: Dict[str, str] = {}
    for pattern in patterns:
        for m in pattern.finditer(raw):
            result[m.group("path").strip()] = m.group("content").strip()
    return result


def validate_outputs(expected: List[str], parsed: Dict[str, str]) -> Tuple[Dict[str, str], List[str]]:
    """校验期望输出是否齐全；路径归一化匹配。"""
    normalized = {k.replace("\\", "/"): v for k, v in parsed.items()}
    out: Dict[str, str] = {}
    missing: List[str] = []
    for exp in expected:
        key = exp.replace("\\", "/")
        if key in normalized:
            out[key] = normalized[key]
        else:
            matched = _fuzzy_match(key, normalized)
            if matched:
                out[key] = normalized[matched]
            else:
                missing.append(exp)
    return out, missing


def _fuzzy_match(expected: str, parsed: Dict[str, str]) -> Optional[str]:
    exp_name = expected.split("/")[-1]
    for p in parsed:
        if p.endswith(exp_name) or p.split("/")[-1] == exp_name:
            return p
    return None

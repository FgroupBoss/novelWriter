"""解析器降级逻辑快速自检（非 pytest）。"""
from pathlib import Path

from novel_workflow.parser import parse_file_blocks

ROOT = Path(__file__).resolve().parent.parent


def test_review_raw():
    raw = (ROOT / "projects/demo_novel/meta/logs/chapter_review_ch004_raw.md").read_text(
        encoding="utf-8"
    )
    p = parse_file_blocks(raw, ["09_chapters/ch004_review.md"])
    assert "ch004_review" in list(p.keys())[0]


def test_loose_file():
    raw = "===FILE:a.md===\nhello\n===FILE:b.md===\nworld"
    p = parse_file_blocks(raw, ["a.md", "b.md"])
    assert len(p) == 2


def test_markdown_paths():
    raw = """```markdown 10_plot_progress.md
# 剧情推进记录
x
```
```markdown 07_foreshadowing.md
# 伏笔
y
```
```markdown 12_timeline.md
# 时间线
z
```"""
    exp = ["10_plot_progress.md", "07_foreshadowing.md", "12_timeline.md"]
    p = parse_file_blocks(raw, exp)
    assert len(p) == 3


def test_heading_split():
    raw = "# 剧情推进记录\nprog\n# 伏笔与回收\nfore\n# 时间线\ntime"
    exp = ["10_plot_progress.md", "07_foreshadowing.md", "12_timeline.md"]
    p = parse_file_blocks(raw, exp)
    assert len(p) == 3


if __name__ == "__main__":
    test_review_raw()
    test_loose_file()
    test_markdown_paths()
    test_heading_split()
    print("all parser tests ok")

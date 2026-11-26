#!/usr/bin/env python3
"""
Audit and optionally clean up bundled Legado bookSources.json.

Usage:
    python tools/check_book_sources.py [path/to/bookSources.json]
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

DEFAULT_JSON_PATH = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "defaultData" / "bookSources.json"
REGRESSION_KEYWORDS = ["斗破苍穹", "十日终焉", "弊不可避"]
USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"


def build_ssl_context() -> ssl.SSLContext:
    ctx = ssl._create_unverified_context()
    return ctx


def fetch_url(url: str, ctx: ssl.SSLContext, timeout: int = 7) -> tuple[str, int, str, str]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            raw = resp.read(65536)
            text = raw.decode("utf-8", errors="replace")
            return (f"HTTP_{resp.status}", len(raw), "", text)
    except urllib.error.HTTPError as err:
        return (f"HTTP_{err.code}", 0, str(err.reason or ""), "")
    except Exception as err:
        return ("ERR", 0, f"{type(err).__name__}: {str(err)[:60]}", "")


def is_fake_alive_html(name: str, body_text: str) -> str | None:
    lower = body_text.lower()
    if "<title>redirect" in lower:
        return "parked redirect page"
    if len(body_text) < 1024 and "window.location.href=" in lower:
        return "short js redirect"
    if len(body_text) < 600 and ('<meta name="robots"' in lower or '<meta http-equiv="refresh"' in lower):
        return "tiny meta shell"
    if "[api]" in name.lower() and (lower.lstrip().startswith("<!doctype html") or lower.lstrip().startswith("<html")):
        return "api returned html homepage"
    return None


def is_empty_json_result(group: str, body_text: str) -> bool:
    niche_groups = ("轻小说·同人", "女频言情", "出版·名著")
    if group in niche_groups:
        return False
    stripped = body_text.lstrip()
    if not stripped.startswith("{"):
        return False
    try:
        obj = json.loads(body_text)
    except Exception:
        return False
    if not isinstance(obj, dict):
        return False
    for key in ("result", "data"):
        val = obj.get(key)
        if isinstance(val, list) and len(val) == 0:
            return True
    return False


def check_schema(source: dict) -> list[str]:
    issues = []
    if not str(source.get("bookSourceName") or "").strip():
        issues.append("missing bookSourceName")
    if not str(source.get("bookSourceUrl") or "").strip():
        issues.append("missing bookSourceUrl")
    if not source.get("ruleSearch"):
        issues.append("missing ruleSearch")
    if not source.get("ruleContent"):
        issues.append("missing ruleContent")
    return issues


def resolve_get_search_url(base_url: str, search_url: str, keyword: str) -> str:
    base = base_url.split("#")[0].rstrip("/")
    encoded_kw = urllib.parse.quote(keyword)
    url = (
        search_url.replace("{{key}}", encoded_kw)
        .replace("{{page}}", "1")
        .replace("searchKey", encoded_kw)
    )
    if url.startswith("http://") or url.startswith("https://"):
        return url
    if url.startswith("/"):
        return base + url
    return base + "/" + url


def probe_source(idx: int, source: dict, keyword: str, ctx: ssl.SSLContext) -> dict:
    name = str(source.get("bookSourceName") or "")
    url = str(source.get("bookSourceUrl") or "")
    group = str(source.get("bookSourceGroup") or "")
    enabled = bool(source.get("enabled", True))
    requires_login = bool(
        str(source.get("loginUrl") or "").strip()
        or str(source.get("loginUi") or "").strip()
    )
    schema_issues = check_schema(source)
    search_url = str(source.get("searchUrl") or "").strip()
    base = url.split("#")[0].strip()

    if not search_url:
        base_status, base_len, base_err, _ = fetch_url(base, ctx) if base.startswith("http") else ("NON_HTTP", 0, "", "")
        category = "DEAD" if base_status.startswith(("HTTP_4", "HTTP_5", "ERR")) else "NO_SEARCH_URL"
        return {
            "index": idx,
            "name": name,
            "url": url,
            "group": group,
            "enabled": enabled,
            "requires_login": requires_login,
            "schema_issues": schema_issues,
            "category": category,
            "detail": f"base={base_status}({base_len}) {base_err}".strip(),
        }

    is_js_or_post = search_url.startswith("@js:") or "<js>" in search_url or ",{" in search_url
    if is_js_or_post:
        if not base.startswith("http"):
            return {
                "index": idx,
                "name": name,
                "url": url,
                "group": group,
                "enabled": enabled,
                "requires_login": requires_login,
                "schema_issues": schema_issues,
                "category": "DEAD",
                "detail": "non-http baseUrl",
            }
        base_status, base_len, base_err, _ = fetch_url(base, ctx)
        is_ok = base_status.startswith(("HTTP_2", "HTTP_3")) and base_len > 0
        return {
            "index": idx,
            "name": name,
            "url": url,
            "group": group,
            "enabled": enabled,
            "requires_login": requires_login,
            "schema_issues": schema_issues,
            "category": "JS_OR_POST_BASE_OK" if is_ok else "DEAD",
            "detail": f"base={base_status}({base_len}) {base_err}".strip(),
        }

    test_url = resolve_get_search_url(url, search_url, keyword)
    search_status, search_len, search_err, body_text = fetch_url(test_url, ctx)
    extra_note = ""
    if search_status.startswith("HTTP_2"):
        fake_reason = is_fake_alive_html(name, body_text)
        if search_len == 0:
            category = "EMPTY_200"
        elif fake_reason:
            category = "FAKE_ALIVE"
            extra_note = fake_reason
        elif is_empty_json_result(group, body_text):
            category = "EMPTY_RESULT"
            extra_note = f"empty list for {keyword}"
        elif search_len >= 200:
            category = "OK_SEARCH"
        else:
            base_status, base_len, _, _ = fetch_url(base, ctx) if base.startswith("http") else ("ERR", 0, "", "")
            category = "OK_SEARCH" if base_status.startswith(("HTTP_2", "HTTP_3")) else "DEAD"
    elif search_status.startswith("HTTP_3"):
        category = "OK_SEARCH"
    else:
        category = "DEAD"

    decoded_text = body_text
    if body_text.lstrip().startswith("{"):
        try:
            decoded_text = json.dumps(json.loads(body_text), ensure_ascii=False)
        except Exception:
            pass
    hit = keyword in decoded_text
    hit_note = f"hit={hit}" if category == "OK_SEARCH" else ""
    detail = " ".join(x for x in [f"search={search_status}({search_len})", hit_note, extra_note, search_err] if x).strip()

    return {
        "index": idx,
        "name": name,
        "url": url,
        "group": group,
        "enabled": enabled,
        "requires_login": requires_login,
        "schema_issues": schema_issues,
        "category": category,
        "detail": detail,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit bundled Legado bookSources.json")
    parser.add_argument("path", nargs="?", default=str(DEFAULT_JSON_PATH), help="Path to bookSources.json")
    parser.add_argument("--keyword", default=REGRESSION_KEYWORDS[0], help="Primary search keyword to probe")
    args = parser.parse_args()

    json_path = Path(args.path)
    if not json_path.exists():
        print(f"File not found: {json_path}", file=sys.stderr)
        return 1

    with json_path.open("r", encoding="utf-8") as f:
        sources = json.load(f)

    if not isinstance(sources, list):
        print("Invalid JSON root: expected array", file=sys.stderr)
        return 1

    ctx = build_ssl_context()
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        results = list(
            executor.map(
                lambda item: probe_source(item[0], item[1], args.keyword, ctx),
                enumerate(sources),
            )
        )

    cat_counts = Counter(r["category"] for r in results)
    group_counts = Counter(r["group"] or "(未分组)" for r in results)
    enabled_count = sum(1 for r in results if r["enabled"])
    schema_errors = [r for r in results if r["schema_issues"]]

    print(f"File: {json_path}")
    print(f"Total sources: {len(results)} | Enabled: {enabled_count} | Disabled: {len(results) - enabled_count}")
    print("Categories:", dict(cat_counts))
    print("Groups:", dict(group_counts))
    if schema_errors:
        print("\nSchema issues:")
        for r in schema_errors:
            print(f"  [{r['index']}] {r['name']}: {r['schema_issues']}")

    print("\nDetailed status:")
    for r in results:
        login_tag = " [LOGIN]" if r["requires_login"] else ""
        en_tag = "ON" if r["enabled"] else "OFF"
        print(f"[{r['index']:02d}] [{en_tag:3s}] [{r['category']:19s}]{login_tag} {r['name']} ({r['group']}) | {r['url']} | {r['detail']}")

    return 0


if __name__ == "__main__":
    sys.exit(main())

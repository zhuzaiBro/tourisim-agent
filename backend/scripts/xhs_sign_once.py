#!/usr/bin/env python3
"""一次性签名：从 stdin 读 JSON，向 stdout 输出签名头。供 Java 子进程调用。"""
from __future__ import annotations

import json
import sys


def parse_cookie(cookie: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for part in cookie.split(";"):
        part = part.strip()
        if "=" in part:
            k, v = part.split("=", 1)
            result[k.strip()] = v.strip()
    return result


def main() -> None:
    req = json.load(sys.stdin)
    cookie = req.get("cookie", "")
    uri = req.get("uri", "")
    payload = req.get("payload") or {}
    cookies = parse_cookie(cookie)

    from xhshow import Xhshow

    client = Xhshow()
    headers = client.sign_headers_post(
        uri=f"https://edith.xiaohongshu.com{uri}",
        cookies=cookies,
        payload=payload,
    )
    json.dump(headers, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)

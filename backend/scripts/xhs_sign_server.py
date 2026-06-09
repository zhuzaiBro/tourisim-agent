#!/usr/bin/env python3
"""
小红书 Web 请求签名微服务（供 Java 后端 cookie 模式调用）。

依赖: pip install xhshow flask

启动: python backend/scripts/xhs_sign_server.py
环境: XHS_SIGN_PORT=8765

Java .env:
  XHS_SIGN_URL=http://127.0.0.1:8765/sign
"""
from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer


def _parse_cookie(cookie: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for part in cookie.split(";"):
        part = part.strip()
        if "=" in part:
            k, v = part.split("=", 1)
            result[k.strip()] = v.strip()
    return result


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/sign":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8")
        try:
            req = json.loads(raw)
            cookie = req.get("cookie", "")
            uri = req.get("uri", "")
            payload = req.get("payload") or {}
            cookies = _parse_cookie(cookie)
            from xhshow import Xhshow

            client = Xhshow()
            headers = client.sign_headers_post(
                uri=f"https://edith.xiaohongshu.com{uri}",
                cookies=cookies,
                payload=payload,
            )
            body = json.dumps(headers, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as e:
            err = json.dumps({"error": str(e)}, ensure_ascii=False).encode("utf-8")
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(err)))
            self.end_headers()
            self.wfile.write(err)

    def log_message(self, fmt, *args):
        print(f"[xhs-sign] {fmt % args}")


def main():
    port = int(os.environ.get("XHS_SIGN_PORT", "8765"))
    server = HTTPServer(("127.0.0.1", port), Handler)
    print(f"xhs sign server listening on http://127.0.0.1:{port}/sign")
    server.serve_forever()


if __name__ == "__main__":
    main()

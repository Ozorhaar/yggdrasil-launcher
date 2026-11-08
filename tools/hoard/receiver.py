#!/usr/bin/env python3
"""The Hoard: receives the Yggdrasil Launcher's nightly layout backups.

Listens on 127.0.0.1 only; Caddy forwards https://homelab.ozorhaar.com/hoard/upload to it and
refuses anyone outside the home's private ranges. Backups arrive already encrypted to the Mac's
own key (~/yggdrasil-keys/hoard.key never leaves the Mac), so this only stores them:
at most 1 MB each, one a minute, the newest 30 kept. Standard library only.

    restore:  python3 restore.py ~/backups/yggdrasil-launcher/<file>.ygg  > layout.json
"""
import datetime
import http.server
import os
import time

DIR = os.path.expanduser("~/backups/yggdrasil-launcher")
KEEP = 30
MAX = 1024 * 1024
MAGIC = b"YGGHOARD1\n"
last = [0.0]


class Hoard(http.server.BaseHTTPRequestHandler):
    server_version = "Hoard"

    def _answer(self, code, text):
        body = (text + "\n").encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path.rstrip("/") not in ("/hoard/upload", "/upload"):
            return self._answer(404, "no")
        n = int(self.headers.get("Content-Length") or 0)
        if n <= len(MAGIC) or n > MAX:
            return self._answer(413, "size")
        if time.time() - last[0] < 60:
            return self._answer(429, "slow down")
        data = self.rfile.read(n)
        if not data.startswith(MAGIC):
            return self._answer(400, "not a hoard backup")
        last[0] = time.time()
        os.makedirs(DIR, mode=0o700, exist_ok=True)
        name = "layout-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".ygg"
        path = os.path.join(DIR, name)
        with open(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as f:
            f.write(data)
        files = sorted(x for x in os.listdir(DIR) if x.startswith("layout-") and x.endswith(".ygg"))
        for old in files[:-KEEP]:
            os.remove(os.path.join(DIR, old))
        self._answer(200, "kept " + name)

    def do_GET(self):
        self._answer(404, "no")

    def log_message(self, fmt, *args):
        print(time.strftime("%Y-%m-%d %H:%M:%S"), self.address_string(), fmt % args, flush=True)


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("127.0.0.1", 8795), Hoard).serve_forever()

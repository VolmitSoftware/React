"""Serves build/jaspr the way Firebase Hosting does, for the browser checks.

A path that names no file gets index.html (the hosting rewrite), so deep
links such as /server/<id>/logs load the app. The listen backlog is 128 and
each request runs on its own thread; python3 -m http.server keeps a backlog
of 5 and resets connections when a page asks for many files at once.

    python3 tool/serve_site.py --port 8820 [--directory build/jaspr]
"""

from __future__ import annotations

import argparse
import functools
import http.server
import posixpath
import urllib.parse
from pathlib import Path
from typing import BinaryIO


class Server(http.server.ThreadingHTTPServer):
    request_queue_size = 128
    daemon_threads = True


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        '.js': 'text/javascript',
        '.mjs': 'text/javascript',
        '.svg': 'image/svg+xml',
        '.json': 'application/json',
        '.webmanifest': 'application/manifest+json',
        '.woff2': 'font/woff2',
    }

    def send_head(self) -> BinaryIO | None:
        """Answers a path that names no file with index.html."""
        path = posixpath.normpath(urllib.parse.unquote(urllib.parse.urlsplit(self.path).path))
        if path != '/' and not (Path(self.directory) / path.lstrip('/')).exists():
            self.path = '/index.html'
        return super().send_head()

    def end_headers(self) -> None:
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, format: str, *args: object) -> None:
        pass


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8820)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--directory', default='build/jaspr')
    options = parser.parse_args()
    handler = functools.partial(Handler, directory=str(Path(options.directory).resolve()))
    with Server((options.bind, options.port), handler) as server:
        print(f'Serving {options.directory} at http://{options.bind}:{options.port}/', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()

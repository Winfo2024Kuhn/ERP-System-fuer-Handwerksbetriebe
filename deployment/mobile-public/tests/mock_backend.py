"""Disposable echo backend, reachable only inside the integration test network."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        data = json.dumps({'path': self.path, 'headers': {k.lower(): v for k, v in self.headers.items()}}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Set-Cookie', 'JSESSIONID=must-not-reach-client; Path=/')
        self.end_headers()
        self.wfile.write(data)

    do_POST = do_GET
    do_PATCH = do_GET
    do_PUT = do_GET
    do_DELETE = do_GET


ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()

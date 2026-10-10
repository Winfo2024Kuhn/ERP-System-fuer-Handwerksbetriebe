"""Real Caddy HTTPS + Nginx tests against an isolated dummy backend; no public tunnel."""
from concurrent.futures import ThreadPoolExecutor
import http.client
import json
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import time
import unittest
import uuid

HERE = Path(__file__).resolve().parents[1]


@unittest.skipUnless(os.environ.get('RUN_PROXY_INTEGRATION') == '1', 'Set RUN_PROXY_INTEGRATION=1 for Docker HTTPS integration tests')
class ProxyTest(unittest.TestCase):
    @classmethod
    def command(cls, args, **kwargs):
        return subprocess.run(args, env=cls.env, text=True, capture_output=True, check=True, **kwargs).stdout.strip()

    @classmethod
    def compose(cls, *args):
        return cls.command(cls.compose_cmd + list(args))

    @classmethod
    def setUpClass(cls):
        cls.project = 'erp-mobile-test-' + uuid.uuid4().hex[:8]
        cls.network = cls.project + '-backend'
        cls.temp = tempfile.TemporaryDirectory()
        cls.env = dict(os.environ, TEST_BACKEND_NETWORK=cls.network)
        cls.compose_cmd = ['docker', 'compose', '--project-name', cls.project,
                           '-f', str(HERE / 'compose.yaml'), '-f', str(HERE / 'tests/compose.test.yaml'), '--profile', 'domain']
        cls.command(['docker', 'network', 'create', '--internal', '--subnet', '172.30.51.0/29', cls.network])
        cls.addClassCleanup(cls.cleanup)
        cls.compose('up', '-d', '--wait', '--wait-timeout', '60', 'app', 'gateway', 'caddy', 'probe', 'rogue')
        port = cls.compose('port', 'caddy', '443')
        cls.port = int(port.rsplit(':', 1)[1])
        caddy = cls.compose('ps', '-q', 'caddy')
        cert = Path(cls.temp.name) / 'root.crt'
        for _ in range(30):
            result = subprocess.run(['docker', 'cp', f'{caddy}:/data/caddy/pki/authorities/local/root.crt', str(cert)], capture_output=True)
            if result.returncode == 0:
                break
            time.sleep(0.2)
        cls.tls = ssl.create_default_context(cafile=str(cert))

    @classmethod
    def cleanup(cls):
        subprocess.run(cls.compose_cmd + ['down', '-v', '--remove-orphans'], env=cls.env, capture_output=True)
        subprocess.run(['docker', 'network', 'rm', cls.network], capture_output=True)
        cls.temp.cleanup()

    def request(self, path, method='GET', headers=None, body=None):
        connection = http.client.HTTPSConnection('localhost', self.port, context=self.tls, timeout=10)
        try:
            try:
                connection.request(method, path, headers=headers or {}, body=body)
            except BrokenPipeError:
                # A rejected oversized upload may receive its response before all bytes are sent.
                pass
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def from_container(self, service, port, headers):
        source = '''import json, urllib.request, urllib.error, sys
req = urllib.request.Request('http://gateway:' + sys.argv[1] + '/api/zeiterfassung/projekte', headers=json.loads(sys.argv[2]))
try:
    response = urllib.request.urlopen(req)
except urllib.error.HTTPError as error:
    response = error
print(json.dumps({'status': response.status, 'body': response.read().decode()}))
'''
        return json.loads(self.compose('exec', '-T', service, 'python', '-c', source, str(port), json.dumps(headers)))

    def test_01_https_headers_and_desktop_session_stripped(self):
        token = '11111111-1111-4111-8111-111111111111'
        status, headers, raw = self.request('/api/zeiterfassung/projekte', headers={
            'Cookie': f'JSESSIONID=desktop-secret; ze_token={token}; XSRF-TOKEN=csrf',
            'Authorization': 'Basic desktop-secret', 'X-ERP-Public-Mobile': '0',
            'X-Forwarded-For': '127.0.0.1', 'CF-Connecting-IP': '127.0.0.2',
            'Forwarded': 'for=127.0.0.3', 'X-Forwarded-Host': 'evil.example',
        })
        self.assertEqual(status, 200)
        seen = json.loads(raw)['headers']
        self.assertEqual(seen['x-erp-public-mobile'], '1')
        self.assertEqual(seen['cookie'], f'ze_token={token}')
        for name in ['authorization', 'forwarded', 'cf-connecting-ip']:
            self.assertNotIn(name, seen)
        self.assertNotEqual(seen['x-forwarded-for'], '127.0.0.1')
        self.assertEqual(seen['x-forwarded-host'], 'localhost')
        self.assertNotIn('Set-Cookie', headers)
        self.assertIn("sandbox; default-src 'none'", headers['Content-Security-Policy'])
        self.assertEqual(headers['X-Content-Type-Options'], 'nosniff')
        self.assertEqual(headers['Cache-Control'], 'no-store')

    def test_02_ui_static_routes_work_without_api_csp(self):
        status, headers, _ = self.request('/zeiterfassung/assets/application.js')
        self.assertEqual(status, 200)
        self.assertNotIn('Content-Security-Policy', headers)

    def test_03_desktop_and_wrong_methods_are_denied(self):
        for method, path in [('GET', '/api/auth/me'), ('POST', '/api/auth/login'), ('GET', '/login'),
                             ('GET', '/api/settings'), ('DELETE', '/api/projekte/7'),
                             ('POST', '/zeiterfassung/'), ('GET', '/actuator/env')]:
            with self.subTest(method=method, path=path):
                self.assertEqual(self.request(path, method)[0], 404)

    def test_04_ambiguous_path_denied(self):
        for path in ['/api/zeiterfassung/projekte;desktop', '/api%2fzeiterfassung/projekte',
                     '/zeiterfassung/../api/auth/me']:
            with self.subTest(path=path):
                self.assertIn(self.request(path)[0], [400, 404])

    def test_05_upload_limit(self):
        status, _, _ = self.request('/api/zeiterfassung/start', 'POST', body=b'x' * (25 * 1024 * 1024 + 1))
        self.assertEqual(status, 413)

    def test_06_tunnel_uses_only_trusted_cf_address(self):
        response = self.from_container('probe', 8082, {'CF-Connecting-IP': '198.51.100.17', 'X-Forwarded-For': '127.0.0.1', 'X-Forwarded-Proto': 'https'})
        self.assertEqual(response['status'], 200)
        seen = json.loads(response['body'])['headers']
        self.assertEqual(seen['x-forwarded-for'], '198.51.100.17')
        self.assertNotIn('cf-connecting-ip', seen)
        for port in [8081, 8082]:
            denied = self.from_container('rogue', port, {'CF-Connecting-IP': '198.51.100.18', 'X-Forwarded-For': '198.51.100.18', 'X-Forwarded-Proto': 'https'})
            self.assertEqual(denied['status'], 403)

    def test_07_token_paths_and_queries_not_logged(self):
        marker = 'SENSITIVE-TEST-MARKER-DO-NOT-LOG'
        self.request(f'/api/mitarbeiter/by-token/{marker}?token={marker}')
        for service in ['caddy', 'gateway']:
            self.assertNotIn(marker, self.compose('logs', '--no-color', service))

    def test_08_rate_limit_cannot_be_bypassed_with_forwarded_headers(self):
        with ThreadPoolExecutor(max_workers=10) as pool:
            replies = list(pool.map(lambda n: self.request('/api/zeiterfassung/projekte', headers={'X-Forwarded-For': f'198.51.100.{n + 1}'}), range(70)))
        limited = [headers for status, headers, _ in replies if status == 429]
        self.assertTrue(limited, 'A burst from one socket origin must be limited despite rotating fake headers')
        self.assertTrue(all(headers.get('Retry-After') == '1' for headers in limited))
        self.assertTrue(all(headers.get('Content-Security-Policy') == "sandbox; default-src 'none'" for headers in limited))

    def test_09_proxy_error_does_not_log_token_url(self):
        marker = 'ERROR-PATH-SECRET-MUST-NOT-APPEAR'
        self.compose('stop', 'gateway')
        self.assertEqual(self.request(f'/api/mitarbeiter/by-token/{marker}?token={marker}')[0], 502)
        self.assertNotIn(marker, self.compose('logs', '--no-color', 'caddy'))


if __name__ == '__main__':
    unittest.main()

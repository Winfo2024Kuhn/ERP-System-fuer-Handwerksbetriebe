"""No backend or secrets required; check actual Compose merge and policy drift."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import os
import unittest

HERE = Path(__file__).resolve().parents[1]
REPO = HERE.parents[1]
spec = importlib.util.spec_from_file_location('sync_routes', HERE / 'sync_routes.py')
routes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(routes)


class ContractTest(unittest.TestCase):
    def test_routes_match_application(self):
        self.assertEqual((HERE / 'mobile-routes.conf').read_text(), routes.render(routes.POLICY.read_text()))

    def test_unknown_java_expression_fails_closed(self):
        with self.assertRaises(ValueError):
            routes.render('private static final String ID = "[0-9]+";\nroute("GET", OTHER),')

    def test_private_ports_replace_public_bindings(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / 'empty.env'
            env_file.write_text('')
            result = subprocess.run(['docker', 'compose', '--env-file', str(env_file), '-f', str(REPO / 'docker-compose.yml'),
                                     '-f', str(HERE / 'erp.override.yaml'), 'config', '--format', 'json'],
                                    capture_output=True, text=True, check=True,
                                    env=dict(os.environ, ERP_PRIVATE_BIND_IP='127.0.0.1'))
        config = json.loads(result.stdout)
        for name, ports in [('app', [8080]), ('mysql', [3306]), ('qdrant', [6333, 6334])]:
            actual = config['services'][name]['ports']
            self.assertEqual(sorted(port['target'] for port in actual), ports)
            self.assertTrue(all(port['host_ip'] == '127.0.0.1' for port in actual))
        app = config['services']['app']
        self.assertEqual(app['environment']['ZEITERFASSUNG_SECURITY_TRUSTED_PROXIES'], '172.30.51.2/32')
        self.assertEqual(app['environment']['SERVER_FORWARD_HEADERS_STRATEGY'], 'none')
        self.assertEqual(app['networks']['mobile_backend']['ipv4_address'], '172.30.51.3')


if __name__ == '__main__':
    unittest.main()

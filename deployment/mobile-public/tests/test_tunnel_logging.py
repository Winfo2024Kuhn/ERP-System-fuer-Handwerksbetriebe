import importlib.util
from pathlib import Path
import unittest

source = Path(__file__).resolve().parents[1] / 'tunnel/run.py'
spec = importlib.util.spec_from_file_location('tunnel_runner', source)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class TunnelLoggingTest(unittest.TestCase):
    def test_prints_only_hostname_even_if_request_has_token(self):
        self.assertEqual(runner.hostname_from_line('error url="https://dummy-trial.trycloudflare.com/api/token/SECRET?token=SECRET"'),
                         'https://dummy-trial.trycloudflare.com')

    def test_unrelated_logs_are_discarded(self):
        self.assertIsNone(runner.hostname_from_line('error request=/api/token/SECRET Authorization=SECRET'))
        self.assertIsNone(runner.hostname_from_line('https://dummy.trycloudflare.com.evil.example/path'))

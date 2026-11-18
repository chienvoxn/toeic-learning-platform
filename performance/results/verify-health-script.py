import csv
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import sys
from threading import Lock, Thread

root = Path(__file__).resolve().parent
script = root.parent / 'health_database.py'

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    requests = 0
    bad = False
    lock = Lock()
    def log_message(self, *args):
        pass
    def do_GET(self):
        with Handler.lock:
            Handler.requests += 1
            count = Handler.requests
        # Một preflight hợp lệ, sau đó trả JSON sai để kiểm tra assertion.
        body = {'status': 'DOWN' if Handler.bad and count > 1 else 'UP', 'database': 'synthetic_db'}
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
server.daemon_threads = True
Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'
try:
    for label, bad, users, expected_code in [('valid', False, ['2', '4'], 0), ('invalid-json-status', True, ['2'], 1)]:
        Handler.requests, Handler.bad = 0, bad
        directory = root / label
        run = subprocess.run([sys.executable, str(script), '--base-url', base,
            '--users', *users, '--iterations', '3', '--ramp-seconds', '0.01',
            '--interval', '0.02', '--cooldown', '0', '--expected-database', 'synthetic_db',
            '--max-connections', '2', '--output', str(directory)], capture_output=True, text=True, timeout=30)
        assert run.returncode == expected_code, run.stdout + run.stderr
        result_file = max(directory.glob('*/summary.json'), key=lambda path: path.stat().st_mtime)
        payload = json.loads(result_file.read_text(encoding='utf-8'))
        for result, count in zip(payload['results'], users):
            assert result['samples'] == int(count) * 3, result
            assert result['failures'] == (result['samples'] if bad else 0), result
            assert result['success_p95_ms'] is None if bad else result['success_p95_ms'] >= 0
        assert Handler.requests == sum(int(count) * 3 + 1 for count in users), Handler.requests
        with result_file.with_suffix('.csv').open(encoding='utf-8-sig') as stream:
            assert len(list(csv.DictReader(stream))) == len(users)
        print(label + ': samples, assertions, exit code, JSON/CSV verified')
finally:
    server.shutdown()

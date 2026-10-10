"""Only emit the temporary hostname; never persist cloudflared request/error URLs."""
import re
import signal
import subprocess
import sys


def hostname_from_line(line):
    # Ignore every other part of the line, including possible query strings/tokens.
    match = re.search(r'https://[a-z0-9-]+\.trycloudflare\.com(?=[/\s|"\']|$)', line)
    return match.group(0) if match else None


def main():
    process = subprocess.Popen(
        ['cloudflared', 'tunnel', '--no-autoupdate', '--url', 'http://gateway:8082'],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: process.terminate())
    seen = set()
    for line in process.stdout:
        hostname = hostname_from_line(line)
        if hostname and hostname not in seen:
            seen.add(hostname)
            print(f'Mobile Testadresse: {hostname}/zeiterfassung/', flush=True)
    code = process.wait()
    if code:
        print('Tunnel beendet. Netzwerk und cloudflared-Version pruefen; keine Anfragedetails protokolliert.', flush=True)
    return code


if __name__ == '__main__':
    sys.exit(main())

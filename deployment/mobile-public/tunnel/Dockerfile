FROM cloudflare/cloudflared:latest@sha256:9b49eed8f62806d5d45ddf59ecefb5710429598ea6d3fcccd2af938f621b2b07 AS cloudflared
FROM python:3.15-slim@sha256:94d3426f1def7db4a106a232ac121bfd975393e5dfd40a619b955fae45fe7913
COPY --from=cloudflared /usr/local/bin/cloudflared /usr/local/bin/cloudflared
COPY run.py /opt/tunnel/run.py
USER 65532:65532
ENTRYPOINT ["python", "-u", "/opt/tunnel/run.py"]

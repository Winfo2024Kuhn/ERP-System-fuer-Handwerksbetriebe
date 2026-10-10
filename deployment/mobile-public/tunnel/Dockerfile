FROM cloudflare/cloudflared:latest@sha256:9b49eed8f62806d5d45ddf59ecefb5710429598ea6d3fcccd2af938f621b2b07 AS cloudflared
FROM python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184ea
COPY --from=cloudflared /usr/local/bin/cloudflared /usr/local/bin/cloudflared
COPY run.py /opt/tunnel/run.py
USER 65532:65532
ENTRYPOINT ["python", "-u", "/opt/tunnel/run.py"]

FROM cloudflare/cloudflared:latest@sha256:4955524aeaa379e154023d5a027c250f7b35041a2e7e950cf13f144bc9b9c5ba AS cloudflared
FROM python:3.15-slim@sha256:94d3426f1def7db4a106a232ac121bfd975393e5dfd40a619b955fae45fe7913
COPY --from=cloudflared /usr/local/bin/cloudflared /usr/local/bin/cloudflared
COPY run.py /opt/tunnel/run.py
USER 65532:65532
ENTRYPOINT ["python", "-u", "/opt/tunnel/run.py"]

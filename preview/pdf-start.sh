#!/usr/bin/env bash
set -euo pipefail
preview_repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$preview_repo"
# The historical branch's mvnw is empty; use an installed Maven without changing it.
mvn -q -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile="$preview_repo/target/preview-pdf-classpath.txt"
preview_classpath="$preview_repo/target/test-classes:$preview_repo/target/classes:$(cat "$preview_repo/target/preview-pdf-classpath.txt")"
preview_temp="$(mktemp -d "${TMPDIR:-/tmp}/en1090-original-pdf.XXXXXX")"
trap 'rm -rf "$preview_temp"' EXIT
cd "$preview_temp"
java -Dnet.bytebuddy.experimental=true -Djdk.attach.allowAttachSelf=true \
  -XX:+EnableDynamicAgentLoading -cp "$preview_classpath" \
  org.example.kalkulationsprogramm.preview.OriginalPdfPreview "${1:-8097}" &
preview_java_pid=$!
trap 'kill "$preview_java_pid" 2>/dev/null || true' TERM INT
wait "$preview_java_pid"

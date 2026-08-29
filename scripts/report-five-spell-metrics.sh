#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

if [[ "$(hostname)" != "ian-kengott-GF63-Thin-11SC" || "$(whoami)" != "ian-kengott" ]]; then
  echo "This measured compile-time report must run on the documented Hermes host." >&2
  exit 1
fi

hermes_java_home=/usr/lib/jvm/java-21-openjdk-amd64
export JAVA_HOME="$hermes_java_home"
export PATH="$hermes_java_home/bin:/usr/bin:/bin"

./gradlew --no-daemon test \
  --rerun-tasks \
  --tests vectorregnum.neoforge.progression.FiveSpellMetricsReportTest

report="$repo_dir/build/minecraft-junit/build/reports/five-spell-metrics.tsv"
test -s "$report"
sed -n '1,20p' "$report"

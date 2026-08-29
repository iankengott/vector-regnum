#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"

if command -v nix >/dev/null 2>&1; then
  task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
  export JAVA_HOME="$task_jdk"
  export PATH="$task_jdk/bin:$PATH"
elif java -version 2>&1 | head -n1 | grep -Eq 'version "21([.]|\")'; then
  task_java=$(readlink -f "$(command -v java)")
  export JAVA_HOME=${task_java%/bin/java}
else
  echo 'Priority 23 verification requires Java 21.' >&2
  exit 1
fi

rg -q 'CURRENT_SCHEMA = 1' src/main/java/vectorregnum/core/effect/PersistentEffectContract.java
rg -q 'MAX_LIFETIME_TICKS = 72_000L' src/main/java/vectorregnum/core/effect/PersistentEffectContract.java
rg -q 'MAX_WORLD_EFFECTS = 1_024' src/main/java/vectorregnum/core/effect/PersistentEffectLedger.java
rg -q 'vector_regnum_persistent_effects' src/main/java/vectorregnum/neoforge/effect/PersistentEffectSavedData.java
rg -q 'COLLAPSE_UNPAID' src/main/java/vectorregnum/neoforge/effect/PersistentEffectService.java
rg -q 'literal\("effect"\)' src/main/java/vectorregnum/neoforge/VectorRegnumCommands.java
rg -q 'literal\("status"\)' src/main/java/vectorregnum/neoforge/VectorRegnumCommands.java
rg -q '"version": 10' src/main/resources/assets/vector_regnum/guide/field_manual.json
rg -q 'PERSISTENT_EFFECT_CHECKPOINT_READY' src/main/java/vectorregnum/neoforge/DevShowcaseController.java
rg -q 'tickPending' src/main/java/vectorregnum/neoforge/NeoForgeVmService.java
rg -q 'persistRegistration' src/test/java/vectorregnum/neoforge/effect/PersistentEffectSavedDataTest.java
test "$(rg -c '^    public void ' src/main/java/vectorregnum/neoforge/gametest/Priority23GameTests.java)" -eq 7
if rg -q 'ACTIVE_FORCES' src/main/java/vectorregnum/neoforge/NeoForgeVmService.java; then
  echo 'Process-local continuing-force state remains in NeoForgeVmService.' >&2
  exit 1
fi

./gradlew --no-daemon test \
  --tests 'vectorregnum.core.effect.*' \
  --tests 'vectorregnum.core.casting.*' \
  --tests 'vectorregnum.neoforge.effect.*' \
  --tests 'vectorregnum.neoforge.guide.*' \
  --tests 'vectorregnum.neoforge.ponder.*'
find src -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

echo PRIORITY23_VERIFY_OK

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
  echo 'Priority 22 verification requires Java 21.' >&2
  exit 1
fi

test "$(rg -c '^    (BARE|RITUAL|ENGRAVING|SPELLBOOK|SCROLL|INSTALLED_CIRCLE)\(' \
  src/main/java/vectorregnum/core/casting/CastingMethod.java)" -eq 6
test "$(rg -c '^    (MANA|CASTING_TIME|UPKEEP|INSTABILITY)\(' \
  src/main/java/vectorregnum/core/casting/ReagentKind.java)" -eq 4
rg -q 'STAGED_REAGENTS' src/main/java/vectorregnum/neoforge/PlayerAttachmentContent.java
rg -q 'registerConfig\(ModConfig.Type.SERVER' src/main/java/vectorregnum/neoforge/VectorRegnumMod.java
rg -q 'GENUINE_SPELL_FAULT' src/main/java/vectorregnum/neoforge/CastService.java
rg -q 'GENUINE_SPELL_FAULT' src/main/java/vectorregnum/neoforge/NeoForgeVmService.java
rg -q 'CAST_QUOTE' src/main/java/vectorregnum/neoforge/ponder/PonderTimeline.java
rg -q '"version": 9' src/main/resources/assets/vector_regnum/guide/field_manual.json
rg -q 'literal\("offering"\)' src/main/java/vectorregnum/neoforge/VectorRegnumCommands.java
rg -q 'UNLOADED_TARGET' src/main/java/vectorregnum/neoforge/NeoForgeVmService.java
rg -q 'offlineOwnerAndUnloadedTargetRefundExactly' \
  src/main/java/vectorregnum/neoforge/gametest/Priority22GameTests.java
rg -q 'duplicateLifecycleCancellationNotifiesTerminalOnce' \
  src/main/java/vectorregnum/neoforge/gametest/Priority22GameTests.java

./gradlew --no-daemon test --tests 'vectorregnum.core.casting.*' \
  --tests 'vectorregnum.core.circle.SpellArtifactTest' \
  --tests 'vectorregnum.neoforge.guide.*' \
  --tests 'vectorregnum.neoforge.ponder.*'
find src -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

echo PRIORITY22_VERIFY_OK

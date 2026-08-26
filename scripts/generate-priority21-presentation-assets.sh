#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
asset_root="$repo_root/src/main/resources/assets/vector_regnum/quasar"

# Frost was a pre-21 spelling. Remove only its known active presentation paths;
# persisted/wire compatibility is handled by the Java aliases, not by assets.
for old_asset in \
  "$asset_root/emitters/presentation/frost.json" \
  "$asset_root/emitters/presentation/ring/frost.json" \
  "$asset_root/emitters/presentation/beam/frost.json" \
  "$asset_root/emitters/presentation/burst/frost.json" \
  "$asset_root/modules/particle_data/presentation/frost.json"; do
  rm -f "$old_asset"
done

declare -A starts=(
  [arcane]=D28CFF [fire]=FFD05A [ice]=D9FAFF [void]=D33EFF
  [water]=65E5FF [air]=F5FFFF [earth]=C8A06A [lightning]=FFF79A
  [time]=FFCB72 [space]=B48CFF [light]=FFF7C2 [dark]=7A7399
  [nature]=B6F08B [sound]=F2A3FF
)
declare -A ends=(
  [arcane]=6D39A8 [fire]=D33A0B [ice]=55A9E8 [void]=321047
  [water]=1A77C5 [air]=8DB8CC [earth]=69452D [lightning]=5F8CFF
  [time]=8A5FBA [space]=1C1238 [light]=FFD45C [dark]=171322
  [nature]=278B45 [sound]=B35DD8
)

for element in "${!starts[@]}"; do
  start="${starts[$element]}"
  end="${ends[$element]}"
  cat > "$asset_root/modules/particle_data/presentation/$element.json" <<EOF
{
  "render_style": "CUBE",
  "should_collide": false,
  "face_velocity": true,
  "velocity_stretch_factor": 0.35,
  "additive": true,
  "modules": [
    {"module": "drag", "strength": 0.92},
    {"module": "color", "gradient": {
      "rgb_points": [{"percent": 0.0, "color": "0x$start"}, {"percent": 1.0, "color": "0x$end"}],
      "alpha_points": [{"percent": 0.0, "alpha": 0.85}, {"percent": 1.0, "alpha": 0.0}]},
      "interpolant": "q.agePercent"}
  ]
}
EOF
  cat > "$asset_root/emitters/presentation/$element.json" <<EOF
{
  "max_lifetime": 10,
  "rate": 2,
  "count": 2,
  "max_particles": 48,
  "emitter_settings": {
    "shape": "vector_regnum:presentation/sphere",
    "particle_settings": "vector_regnum:presentation/motes"
  },
  "particle_data": "vector_regnum:presentation/$element"
}
EOF
  for family in ring beam burst; do
    case "$family" in
      ring) shape=torus; lifetime=12; rate=6; count=4; max=48 ;;
      beam) shape=cylinder; lifetime=8; rate=10; count=5; max=48 ;;
      burst) shape=sphere; lifetime=8; rate=12; count=6; max=40 ;;
    esac
    cat > "$asset_root/emitters/presentation/$family/$element.json" <<EOF
{
  "max_lifetime": $lifetime,
  "rate": $rate,
  "count": $count,
  "max_particles": $max,
  "emitter_settings": {
    "shape": "vector_regnum:presentation/$shape",
    "particle_settings": "vector_regnum:presentation/motes"
  },
  "particle_data": "vector_regnum:presentation/$element"
}
EOF
  done
done

cat > "$asset_root/emitters/presentation/light_motif.json" <<'EOF'
{
  "max_lifetime": 8,
  "rate": 4,
  "count": 2,
  "max_particles": 32,
  "emitter_settings": {
    "shape": "vector_regnum:presentation/sphere",
    "particle_settings": "vector_regnum:presentation/motes"
  },
  "particle_data": "vector_regnum:presentation/light"
}
EOF

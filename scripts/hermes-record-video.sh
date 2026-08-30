#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-record-video.sh [SECONDS]

Record the unique Hermes Minecraft client window for 3–30 seconds through its
Xwayland window ID. The default is 12 seconds. The untouched WebM source and a
review-ready MP4 copy are placed in ignored visual-evidence/.
USAGE
}

duration="${1:-12}"
case "$duration" in
    -h|--help|help) usage; exit 0 ;;
esac
(( $# <= 1 )) || { usage >&2; vr_die "too many arguments"; }
[[ "$duration" =~ ^[0-9]+$ ]] && (( duration >= 3 && duration <= 30 )) ||
    vr_die "recording duration must be an integer from 3 through 30 seconds"

repo_root="$(vr_repo_root)"
vr_require_local_repo "$repo_root"
vr_check_remote_identity
vr_require_remote_marker

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
[[ "$timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || vr_die "could not create a safe timestamp"
basename="hermes-window-${timestamp}"
remote_evidence_dir="$VR_REMOTE_DIR/visual-evidence"
remote_source="$remote_evidence_dir/${basename}-source.webm"
remote_video="$remote_evidence_dir/${basename}.mp4"
local_evidence_dir="$repo_root/visual-evidence"
local_source="$local_evidence_dir/${basename}-source.webm"
local_video="$local_evidence_dir/${basename}.mp4"

mkdir -p -- "$local_evidence_dir"
vr_note "Recording the focused Hermes Minecraft window for ${duration}s..."
vr_ssh bash -s -- \
    "$VR_REMOTE_DIR" "$remote_evidence_dir" "$remote_source" "$remote_video" "$duration" <<'REMOTE'
set -euo pipefail

remote_dir="$1"
evidence_dir="$2"
source_file="$3"
video_file="$4"
duration="$5"

[[ "$remote_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
[[ "$evidence_dir" == "$remote_dir/visual-evidence" ]] || exit 1
[[ "$source_file" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*/visual-evidence/hermes-window-[0-9]{8}T[0-9]{6}Z-source[.]webm$ ]] || exit 1
[[ "$video_file" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*/visual-evidence/hermes-window-[0-9]{8}T[0-9]{6}Z[.]mp4$ ]] || exit 1
[[ "$duration" =~ ^[0-9]+$ ]] && (( duration >= 3 && duration <= 30 )) || exit 1
[[ -f "$remote_dir/.vector-regnum-hermes-worktree" && ! -L "$remote_dir/.vector-regnum-hermes-worktree" ]] || exit 1
[[ "$(<"$remote_dir/.vector-regnum-hermes-worktree")" == "vector-regnum-hermes-worktree-v1" ]] || exit 1
[[ ! -L "$evidence_dir" ]] || exit 1
mkdir -p -- "$evidence_dir"
[[ ! -e "$source_file" && ! -e "$video_file" ]] || {
    printf 'refusing to replace existing video evidence\n' >&2
    exit 1
}
systemctl --user is-active --quiet vector-regnum-dev-server.service || {
    printf 'Vector-Regnum development server is not active\n' >&2
    exit 1
}
systemctl --user is-active --quiet vector-regnum-dev-client.service || {
    printf 'Vector-Regnum development client is not active\n' >&2
    exit 1
}
ss -H -ltn 'sport = :25575' | "$remote_dir/scripts/check-dev-listener.sh" 25575 >/dev/null

for dependency in xprop xwininfo wmctrl ffmpeg ffprobe; do
    command -v "$dependency" >/dev/null || {
        printf 'video capture dependency is missing: %s\n' "$dependency" >&2
        exit 1
    }
done

session_environment="$(systemctl --user show-environment)"
session_value() {
    local name="$1"
    sed -n "s/^${name}=//p" <<< "$session_environment" | head -n 1
}
export DISPLAY="$(session_value DISPLAY)"
export XAUTHORITY="$(session_value XAUTHORITY)"
[[ -n "$DISPLAY" && -f "$XAUTHORITY" ]] || {
    printf 'live Xwayland display environment is incomplete\n' >&2
    exit 1
}

client_windows=()
client_list="$(xprop -root _NET_CLIENT_LIST | sed -n 's/^.*window id # //p' | tr -d ',')"
for candidate in $client_list; do
    [[ "$candidate" =~ ^0x[0-9a-fA-F]+$ ]] || continue
    identity="$(xprop -id "$candidate" _NET_WM_NAME WM_CLASS 2>/dev/null || true)"
    if grep -Fq 'WM_CLASS(STRING) = "Minecraft* 1.21.1", "Minecraft* 1.21.1"' \
            <<< "$identity" && grep -Fq 'Multiplayer (3rd-party Server)' <<< "$identity"; then
        client_windows+=("$candidate")
        window_identity="$identity"
    fi
done
(( ${#client_windows[@]} == 1 )) || {
    printf 'expected exactly one Vector-Regnum Minecraft client window; found %d\n' \
        "${#client_windows[@]}" >&2
    exit 1
}
active_window="${client_windows[0]}"
wmctrl -ia "$active_window"
sleep 0.5
window_info="$(xwininfo -frame -id "$active_window")"
window_x="$(sed -n 's/^[[:space:]]*Absolute upper-left X:[[:space:]]*//p' <<< "$window_info")"
window_y="$(sed -n 's/^[[:space:]]*Absolute upper-left Y:[[:space:]]*//p' <<< "$window_info")"
window_width="$(sed -n 's/^[[:space:]]*Width:[[:space:]]*//p' <<< "$window_info")"
window_height="$(sed -n 's/^[[:space:]]*Height:[[:space:]]*//p' <<< "$window_info")"
[[ "$window_x" =~ ^[0-9]+$ && "$window_y" =~ ^[0-9]+$ ]] || exit 1
[[ "$window_width" =~ ^[0-9]+$ && "$window_height" =~ ^[0-9]+$ ]] || exit 1
(( window_width > 0 && window_height > 0 )) || exit 1

ffmpeg -hide_banner -loglevel error -nostdin -y \
    -f x11grab -framerate 30 -window_id "$active_window" -i "$DISPLAY" \
    -t "$duration" -an -c:v libvpx -deadline realtime -cpu-used 8 -crf 18 -b:v 0 \
    "$source_file"
[[ -s "$source_file" ]] || {
    printf 'Xwayland did not produce a non-empty source recording\n' >&2
    exit 1
}
ffmpeg -hide_banner -loglevel error -nostdin -y \
    -i "$source_file" -an -c:v libx264 -preset veryfast -crf 18 \
    -fps_mode passthrough -pix_fmt yuv420p -movflags +faststart "$video_file"
chmod 0644 "$source_file" "$video_file"
[[ -s "$video_file" ]] || exit 1
printf 'RECORDED_WINDOW %s\n' "$window_identity"
ffprobe -v error -select_streams v:0 \
    -show_entries stream=width,height,avg_frame_rate \
    -show_entries format=duration \
    -of default=noprint_wrappers=1 "$video_file"
REMOTE

for remote_local in "$remote_source:$local_source" "$remote_video:$local_video"; do
    remote_path="${remote_local%%:*}"
    local_path="${remote_local#*:}"
    scp -q -p -o BatchMode=yes -o "ConnectTimeout=$VR_SSH_TIMEOUT" \
        -- "$VR_HERMES_HOST:$remote_path" "$local_path"
    [[ -s "$local_path" ]] || vr_die "copied video evidence is empty: $local_path"
done

vr_note "source=$local_source"
vr_note "video=$local_video"

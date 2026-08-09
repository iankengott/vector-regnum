#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-screenshot.sh [window|desktop]

Capture a dated screenshot through the live Hermes GNOME session and copy it
to the repository's ignored visual-evidence/ directory.

  window   Capture the currently focused window, including its frame (default).
  desktop  Capture the whole GNOME desktop.

For a Minecraft-only image, focus the Minecraft window before using `window`.
USAGE
}

mode="${1:-window}"
(( $# <= 1 )) || { usage >&2; vr_die "too many arguments"; }
case "$mode" in
    window|desktop) ;;
    -h|--help|help) usage; exit 0 ;;
    *) usage >&2; vr_die "unknown capture mode: $mode" ;;
esac

repo_root="$(vr_repo_root)"
vr_require_local_repo "$repo_root"
vr_check_remote_identity
vr_require_remote_marker

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
[[ "$timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || vr_die "could not create a safe timestamp"
filename="hermes-${mode}-${timestamp}.png"
remote_evidence_dir="$VR_REMOTE_DIR/visual-evidence"
remote_file="$remote_evidence_dir/$filename"
local_evidence_dir="$repo_root/visual-evidence"
local_file="$local_evidence_dir/$filename"

mkdir -p -- "$local_evidence_dir"

vr_note "Capturing Hermes GNOME $mode screenshot..."
vr_ssh bash -s -- "$mode" "$remote_evidence_dir" "$remote_file" <<'REMOTE'
set -euo pipefail

mode="$1"
evidence_dir="$2"
output_file="$3"

[[ "$mode" == "window" || "$mode" == "desktop" ]] || exit 1
[[ "$evidence_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*/visual-evidence$ ]] || exit 1
[[ "$output_file" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*/visual-evidence/hermes-(window|desktop)-[0-9]{8}T[0-9]{6}Z[.]png$ ]] || exit 1
[[ "$output_file" == "$evidence_dir/"* ]] || exit 1

export XDG_RUNTIME_DIR=/run/user/1000
export DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus
[[ -S "$XDG_RUNTIME_DIR/bus" ]] || {
    printf 'GNOME session bus is unavailable\n' >&2
    exit 1
}
[[ -S "$XDG_RUNTIME_DIR/at-spi/bus" ]] || {
    printf 'GNOME accessibility bus is unavailable\n' >&2
    exit 1
}

session_environment="$(systemctl --user show-environment)"
session_value() {
    local name="$1"
    sed -n "s/^${name}=//p" <<< "$session_environment" | head -n 1
}

export DISPLAY="$(session_value DISPLAY)"
export WAYLAND_DISPLAY="$(session_value WAYLAND_DISPLAY)"
export XAUTHORITY="$(session_value XAUTHORITY)"
[[ -n "$DISPLAY" && -n "$WAYLAND_DISPLAY" && -f "$XAUTHORITY" ]] || {
    printf 'live GNOME display environment is incomplete\n' >&2
    exit 1
}

[[ ! -L "$evidence_dir" ]] || {
    printf 'refusing symlinked visual-evidence directory\n' >&2
    exit 1
}
mkdir -p -- "$evidence_dir"
[[ ! -e "$output_file" ]] || {
    printf 'refusing to replace existing screenshot: %s\n' "$output_file" >&2
    exit 1
}

window_x=
window_y=
window_width=
window_height=
if [[ "$mode" == "window" ]]; then
    for dependency in xprop xwininfo ffmpeg ffprobe; do
        command -v "$dependency" >/dev/null || {
            printf 'window capture dependency is missing: %s\n' "$dependency" >&2
            exit 1
        }
    done

    # Preserve the target before the portal temporarily takes focus.
    active_window="$(xprop -root _NET_ACTIVE_WINDOW | sed -n 's/^.*window id # //p')"
    [[ "$active_window" =~ ^0x[0-9a-fA-F]+$ && "$active_window" != "0x0" ]] || {
        printf 'no focused Xwayland window is available for window capture\n' >&2
        exit 1
    }
    window_info="$(xwininfo -frame -id "$active_window")"
    window_x="$(sed -n 's/^[[:space:]]*Absolute upper-left X:[[:space:]]*//p' <<< "$window_info")"
    window_y="$(sed -n 's/^[[:space:]]*Absolute upper-left Y:[[:space:]]*//p' <<< "$window_info")"
    window_width="$(sed -n 's/^[[:space:]]*Width:[[:space:]]*//p' <<< "$window_info")"
    window_height="$(sed -n 's/^[[:space:]]*Height:[[:space:]]*//p' <<< "$window_info")"
    [[ "$window_x" =~ ^-?[0-9]+$ && "$window_y" =~ ^-?[0-9]+$ ]] || exit 1
    [[ "$window_width" =~ ^[0-9]+$ && "$window_height" =~ ^[0-9]+$ ]] || exit 1
fi

# GNOME rejects org.gnome.Shell.Screenshot calls from an SSH-owned D-Bus peer.
# Ask GNOME's screenshot portal backend instead, then approve only the portal's
# named Share button through AT-SPI. The portal captures before showing that
# confirmation, so its returned image contains neither the prompt nor the click.
portal_file="$(python3 - <<'PY'
from gi.repository import Gio, GLib
from pathlib import Path
from urllib.parse import unquote, urlparse
import re
import secrets
import threading
import time

session = Gio.bus_get_sync(Gio.BusType.SESSION, None)
atspi = Gio.DBusConnection.new_for_address_sync(
    "unix:path=/run/user/1000/at-spi/bus",
    Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT
    | Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION,
    None,
    None,
)
result = {"reply": None, "error": None}
request_handle = (
    "/org/freedesktop/portal/desktop/request/vector_regnum/"
    + secrets.token_hex(8)
)


def portal_call():
    try:
        result["reply"] = session.call_sync(
            "org.freedesktop.impl.portal.desktop.gnome",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.impl.portal.Screenshot",
            "Screenshot",
            GLib.Variant(
                "(ossa{sv})",
                (
                    request_handle,
                    "vector-regnum-dev",
                    "",
                    {"interactive": GLib.Variant("b", False)},
                ),
            ),
            GLib.VariantType.new("(ua{sv})"),
            Gio.DBusCallFlags.NONE,
            30_000,
            None,
        ).unpack()
    except Exception as exc:
        result["error"] = repr(exc)


def call(destination, path, interface, method, parameters=None, output_type=None):
    try:
        return atspi.call_sync(
            destination,
            path,
            interface,
            method,
            parameters,
            GLib.VariantType.new(output_type) if output_type else None,
            Gio.DBusCallFlags.NONE,
            1_500,
            None,
        ).unpack()
    except Exception:
        return None


def accessible_property(destination, path, name):
    response = call(
        destination,
        path,
        "org.freedesktop.DBus.Properties",
        "Get",
        GLib.Variant("(ss)", ("org.a11y.atspi.Accessible", name)),
        "(v)",
    )
    return None if response is None else response[0]


def find_action(destination, path, wanted_name, depth=0):
    if depth > 24:
        return None
    if accessible_property(destination, path, "Name") == wanted_name:
        interfaces = call(
            destination,
            path,
            "org.a11y.atspi.Accessible",
            "GetInterfaces",
            output_type="(as)",
        )
        if interfaces and "org.a11y.atspi.Action" in interfaces[0]:
            return destination, path
    children = call(
        destination,
        path,
        "org.a11y.atspi.Accessible",
        "GetChildren",
        output_type="(a(so))",
    )
    for child_destination, child_path in children[0] if children else ():
        found = find_action(
            child_destination, child_path, wanted_name, depth + 1
        )
        if found:
            return found
    return None


thread = threading.Thread(target=portal_call, daemon=True)
thread.start()
share_action = None
for _ in range(80):
    applications = call(
        "org.a11y.atspi.Registry",
        "/org/a11y/atspi/accessible/root",
        "org.a11y.atspi.Accessible",
        "GetChildren",
        output_type="(a(so))",
    )
    for app_destination, app_path in applications[0] if applications else ():
        if (
            accessible_property(app_destination, app_path, "Name")
            == "xdg-desktop-portal-gnome"
        ):
            share_action = find_action(app_destination, app_path, "Share")
            if share_action:
                break
    if share_action:
        break
    time.sleep(0.25)

if not share_action:
    raise SystemExit("GNOME screenshot portal Share button was not found")
clicked = call(
    share_action[0],
    share_action[1],
    "org.a11y.atspi.Action",
    "DoAction",
    GLib.Variant("(i)", (0,)),
    "(b)",
)
if clicked != (True,):
    raise SystemExit("GNOME screenshot portal Share action failed")

thread.join(15)
if thread.is_alive():
    raise SystemExit("GNOME screenshot portal did not return after Share")
if result["error"]:
    raise SystemExit("GNOME screenshot portal failed: " + result["error"])
response_code, response_values = result["reply"]
uri = response_values.get("uri")
if response_code != 0 or not uri:
    raise SystemExit(
        f"GNOME screenshot portal returned response code {response_code}"
    )

parsed = urlparse(uri)
if parsed.scheme != "file" or parsed.netloc not in ("", "localhost"):
    raise SystemExit("GNOME screenshot portal returned a non-local URI")
portal_path = Path(unquote(parsed.path))
if (
    portal_path.parent != Path("/home/ian-kengott/Pictures")
    or not re.fullmatch(r"Screenshot(?:-[0-9]+)?[.]png", portal_path.name)
    or portal_path.is_symlink()
    or not portal_path.is_file()
    or portal_path.stat().st_size == 0
):
    raise SystemExit("GNOME screenshot portal returned an unsafe image path")
print(portal_path)
PY
)"

[[ "$portal_file" =~ ^/home/ian-kengott/Pictures/Screenshot(-[0-9]+)?[.]png$ ]] || {
    printf 'GNOME screenshot portal returned an unsafe path\n' >&2
    exit 1
}
[[ -f "$portal_file" && ! -L "$portal_file" && -s "$portal_file" ]] || {
    printf 'GNOME screenshot portal image is missing or unsafe\n' >&2
    exit 1
}

if [[ "$mode" == "desktop" ]]; then
    install -m 0644 -- "$portal_file" "$output_file"
else
    dimensions="$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=width,height -of csv=s=x:p=0 -- "$portal_file")"
    [[ "$dimensions" =~ ^([0-9]+)x([0-9]+)$ ]] || exit 1
    screen_width="${BASH_REMATCH[1]}"
    screen_height="${BASH_REMATCH[2]}"

    if (( window_x < 0 )); then
        (( window_width += window_x ))
        window_x=0
    fi
    if (( window_y < 0 )); then
        (( window_height += window_y ))
        window_y=0
    fi
    (( window_x < screen_width && window_y < screen_height )) || exit 1
    if (( window_x + window_width > screen_width )); then
        window_width=$(( screen_width - window_x ))
    fi
    if (( window_y + window_height > screen_height )); then
        window_height=$(( screen_height - window_y ))
    fi
    (( window_width > 0 && window_height > 0 )) || exit 1

    ffmpeg -hide_banner -loglevel error -nostdin -y \
        -i "$portal_file" \
        -vf "crop=${window_width}:${window_height}:${window_x}:${window_y}" \
        "$output_file"
    chmod 0644 "$output_file"
fi

[[ -s "$output_file" ]] || {
    printf 'GNOME did not produce a non-empty screenshot: %s\n' "$output_file" >&2
    exit 1
}
REMOTE

scp \
    -q \
    -p \
    -o BatchMode=yes \
    -o "ConnectTimeout=$VR_SSH_TIMEOUT" \
    -- "$VR_HERMES_HOST:$remote_file" "$local_file"

[[ -s "$local_file" ]] || vr_die "copied screenshot is empty: $local_file"
vr_note "$local_file"

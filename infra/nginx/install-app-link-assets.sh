#!/bin/sh
set -eu

fail() {
    printf 'APP LINK ASSET INSTALL FAILED: %s\n' "$*" >&2
    exit 1
}

usage() {
    printf 'Usage: %s --release-sha256 AA:BB:...:FF\n' "$0" >&2
    exit 2
}

[ "$#" -eq 2 ] || usage
[ "$1" = "--release-sha256" ] || usage

RAW_FINGERPRINT="$2"
LOWER_FINGERPRINT="$(printf '%s' "$RAW_FINGERPRINT" | tr '[:upper:]' '[:lower:]')"

case "$LOWER_FINGERPRINT" in
    *debug*|*placeholder*|*change_me*|*change-me*|*replace*|*not_verified*|*not-verified*|*todo*|*example*)
        fail 'a placeholder or debug fingerprint is not allowed'
        ;;
esac

FINGERPRINT="$(printf '%s' "$RAW_FINGERPRINT" | tr '[:lower:]' '[:upper:]')"
if ! printf '%s\n' "$FINGERPRINT" | grep -Eq '^([0-9A-F]{2}:){31}[0-9A-F]{2}$'; then
    fail 'release SHA-256 must contain exactly 32 colon-separated hexadecimal bytes'
fi

REPOSITORY_DEBUG_FINGERPRINT='FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C'
[ "$FINGERPRINT" != "$REPOSITORY_DEBUG_FINGERPRINT" ] || \
    fail 'the repository Android debug certificate fingerprint is not allowed'

UNIQUE_BYTE_COUNT="$(
    printf '%s\n' "$FINGERPRINT" |
        tr ':' '\n' |
        sort -u |
        wc -l |
        tr -d '[:space:]'
)"
[ "$UNIQUE_BYTE_COUNT" -gt 1 ] || fail 'a repeated-byte placeholder fingerprint is not allowed'

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPOSITORY_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
HTML_SOURCE="$SCRIPT_DIR/www/share/report.html"
MANIFEST_SOURCE="$SCRIPT_DIR/www/share/report.webmanifest"
SCRIPT_SOURCE="$SCRIPT_DIR/www/share/report-open.js"
IMAGE_SOURCE="$REPOSITORY_ROOT/frontend/assets/branding/logo.png"
ASSET_ROOT="${APP_LINK_ASSET_ROOT:-/var/www/ssabangpalbang/app-link-assets}"

[ -s "$HTML_SOURCE" ] || fail "missing report share page: $HTML_SOURCE"
[ -s "$MANIFEST_SOURCE" ] || fail "missing report web manifest: $MANIFEST_SOURCE"
[ -s "$SCRIPT_SOURCE" ] || fail "missing report landing script: $SCRIPT_SOURCE"
[ -s "$IMAGE_SOURCE" ] || fail "missing brand image: $IMAGE_SOURCE"

for REQUIRED_HTML_FRAGMENT in \
    '__REPORT_URL__' \
    '__REPORT_OPEN_URL__' \
    '<meta property="og:title" content="싸방팔방 임장 리포트"' \
    'https://portfolio.example.com/share/report-card.png' \
    'id="install-app"' \
    'href="__REPORT_OPEN_URL__"' \
    'src="/share/report-open.js"'
do
    grep -Fq "$REQUIRED_HTML_FRAGMENT" "$HTML_SOURCE" || \
        fail "report share page is missing required content: $REQUIRED_HTML_FRAGMENT"
done

INSTALL_LINK_COUNT="$(
    tr '\n' ' ' < "$HTML_SOURCE" |
        grep -Eo 'id="install-app"[[:space:]]+href="https://[^"[:space:]]+"' |
        wc -l |
        tr -d '[:space:]'
)"
[ "$INSTALL_LINK_COUNT" -eq 1 ] || \
    fail 'report share page must contain exactly one HTTPS install-app link'

grep -Fq '"platform": "play"' "$MANIFEST_SOURCE" || \
    fail 'report web manifest is missing the Play relationship'
grep -Fq '"id": "com.ssafy.ssabangpalbang"' "$MANIFEST_SOURCE" || \
    fail 'report web manifest is missing the Android package ID'
grep -Fq "const ANDROID_PACKAGE_ID = 'com.ssafy.ssabangpalbang'" \
    "$SCRIPT_SOURCE" || fail 'report landing script is missing the Android package ID'
grep -Fq 'getInstalledRelatedApps' "$SCRIPT_SOURCE" || \
    fail 'report landing script is missing installed-app detection'

PNG_SIGNATURE="$(od -An -tx1 -N8 "$IMAGE_SOURCE" | tr -d '[:space:]')"
[ "$PNG_SIGNATURE" = '89504e470d0a1a0a' ] || fail 'brand image is not a PNG file'

PNG_WIDTH="$(od -An -tx1 -j16 -N4 "$IMAGE_SOURCE" | tr -d '[:space:]')"
PNG_HEIGHT="$(od -An -tx1 -j20 -N4 "$IMAGE_SOURCE" | tr -d '[:space:]')"
[ "$PNG_WIDTH" = '0000087c' ] && [ "$PNG_HEIGHT" = '000002d4' ] || \
    fail 'brand image dimensions must remain 2172x724 to match the OG metadata'

case "$ASSET_ROOT" in
    ''|/|*..*) fail 'APP_LINK_ASSET_ROOT must be a dedicated app-link-assets directory' ;;
    */app-link-assets) ;;
    *) fail 'APP_LINK_ASSET_ROOT must end with /app-link-assets' ;;
esac

umask 022
install -d -m 0755 "$ASSET_ROOT" "$ASSET_ROOT/releases"

RELEASE_ID="$(date -u '+%Y%m%dT%H%M%SZ')-$$"
STAGING_DIR="$ASSET_ROOT/releases/.${RELEASE_ID}.staging"
RELEASE_DIR="$ASSET_ROOT/releases/$RELEASE_ID"
CURRENT_LINK_TEMP="$ASSET_ROOT/.current.$RELEASE_ID"
LOCK_FILE="$ASSET_ROOT/.install.lock"

command -v flock >/dev/null 2>&1 || fail 'flock is required for App Link asset installation'
exec 9>"$LOCK_FILE"
flock -n 9 || fail 'another App Link asset installation is already running'

cleanup() {
    rm -rf -- "$STAGING_DIR"
    rm -f -- "$CURRENT_LINK_TEMP"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

install -d -m 0755 "$STAGING_DIR/.well-known" "$STAGING_DIR/share"
install -m 0644 "$HTML_SOURCE" "$STAGING_DIR/share/report.html"
install -m 0644 "$MANIFEST_SOURCE" "$STAGING_DIR/share/report.webmanifest"
install -m 0644 "$SCRIPT_SOURCE" "$STAGING_DIR/share/report-open.js"
install -m 0644 "$IMAGE_SOURCE" "$STAGING_DIR/share/report-card.png"

printf '%s\n' \
    '[' \
    '  {' \
    '    "relation": ["delegate_permission/common.handle_all_urls"],' \
    '    "target": {' \
    '      "namespace": "android_app",' \
    '      "package_name": "com.ssafy.ssabangpalbang",' \
    "      \"sha256_cert_fingerprints\": [\"$FINGERPRINT\"]" \
    '    }' \
    '  }' \
    ']' > "$STAGING_DIR/.well-known/assetlinks.json"

grep -Fq '"package_name": "com.ssafy.ssabangpalbang"' \
    "$STAGING_DIR/.well-known/assetlinks.json" || fail 'generated package name validation failed'
grep -Fq "\"$FINGERPRINT\"" \
    "$STAGING_DIR/.well-known/assetlinks.json" || fail 'generated fingerprint validation failed'

mv -- "$STAGING_DIR" "$RELEASE_DIR"
ln -s "releases/$RELEASE_ID" "$CURRENT_LINK_TEMP"
mv -Tf -- "$CURRENT_LINK_TEMP" "$ASSET_ROOT/current"

cleanup
trap - EXIT INT TERM
printf 'Installed App Link assets at %s/current\n' "$ASSET_ROOT"

#!/usr/bin/env bash
set -Eeuo pipefail

HTTP_BASE_URL="${HTTP_BASE_URL:-http://portfolio.example.com}"
HTTPS_BASE_URL="${HTTPS_BASE_URL:-https://portfolio.example.com}"
EXPECTED_REST_STATUS="${EXPECTED_REST_STATUS:-401}"
EXPECTED_REPORT_STATUS="${EXPECTED_REPORT_STATUS:-200}"
EXPECTED_APP_LINK_SHA256="${EXPECTED_APP_LINK_SHA256:-}"
WEBSOCKET_KEY="dGhlIHNhbXBsZSBub25jZQ=="
EXPECTED_WEBSOCKET_ACCEPT="s3pPLMBiTxaQ9kYGzzhZRbK+xOo="

HTTP_BASE_URL="${HTTP_BASE_URL%/}"
HTTPS_BASE_URL="${HTTPS_BASE_URL%/}"

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

fail() {
    echo "SMOKE TEST FAILED: $*" >&2
    exit 1
}

last_http_status() {
    awk '/^HTTP\// { status=$2 } END { print status }' "$1"
}

last_location() {
    awk '
        /^[Ll]ocation:/ {
            sub(/^[^:]*:[[:space:]]*/, "")
            sub(/\r$/, "")
            location=$0
        }
        END { print location }
    ' "$1"
}

last_header_value() {
    awk -v header_name="$2" '
        index(tolower($0), tolower(header_name) ":") == 1 {
            sub(/^[^:]*:[[:space:]]*/, "")
            sub(/\r$/, "")
            value=$0
        }
        END { print value }
    ' "$1"
}

case "$EXPECTED_REPORT_STATUS" in
    200|401) ;;
    *) fail "EXPECTED_REPORT_STATUS must be 200 or 401" ;;
esac

if [ "$EXPECTED_REPORT_STATUS" = "401" ] && \
   [ -n "$EXPECTED_APP_LINK_SHA256" ]; then
    fail "EXPECTED_APP_LINK_SHA256 requires EXPECTED_REPORT_STATUS=200"
fi

echo "===== HTTP TO HTTPS REDIRECT ====="
curl \
    --silent \
    --show-error \
    --max-time 10 \
    --dump-header "$TEMP_DIR/http-redirect.headers" \
    --output /dev/null \
    "$HTTP_BASE_URL/actuator/health"

REDIRECT_STATUS="$(last_http_status "$TEMP_DIR/http-redirect.headers")"
REDIRECT_LOCATION="$(last_location "$TEMP_DIR/http-redirect.headers")"

[ "$REDIRECT_STATUS" = "308" ] || \
    fail "expected HTTP 308, received ${REDIRECT_STATUS:-none}"
[ "$REDIRECT_LOCATION" = "$HTTPS_BASE_URL/actuator/health" ] || \
    fail "unexpected redirect location: ${REDIRECT_LOCATION:-none}"

echo "===== EXTERNAL ACTUATOR HEALTH ====="
if ! HEALTH_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --output "$TEMP_DIR/health.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/actuator/health"
)"; then
    fail "external health request failed"
fi

HEALTH_BODY="$(cat "$TEMP_DIR/health.body")"
COMPACT_HEALTH="$(printf '%s' "$HEALTH_BODY" | tr -d '[:space:]')"

[ "$HEALTH_STATUS" = "200" ] || \
    fail "expected health HTTP 200, received ${HEALTH_STATUS:-none}"
[ "$COMPACT_HEALTH" = '{"status":"UP"}' ] || \
    fail "unexpected health response: $HEALTH_BODY"

echo "===== REST REVERSE PROXY ====="
REST_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --output "$TEMP_DIR/rest.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/api/v1/members/me"
)"

[ "$REST_STATUS" = "$EXPECTED_REST_STATUS" ] || \
    fail "expected REST HTTP $EXPECTED_REST_STATUS, received $REST_STATUS"

echo "===== REPORT SHARE APP LINK ====="
REPORT_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/report.headers" \
        --output "$TEMP_DIR/report.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/report/1"
)"

[ "$REPORT_STATUS" = "$EXPECTED_REPORT_STATUS" ] || \
    fail "expected report share HTTP $EXPECTED_REPORT_STATUS, received $REPORT_STATUS"

if [ "$EXPECTED_REPORT_STATUS" = "200" ]; then

REPORT_CONTENT_TYPE="$(last_header_value "$TEMP_DIR/report.headers" Content-Type)"
case "$(printf '%s' "$REPORT_CONTENT_TYPE" | tr '[:upper:]' '[:lower:]')" in
    text/html*) ;;
    *) fail "unexpected report share Content-Type: ${REPORT_CONTENT_TYPE:-none}" ;;
esac

REPORT_DELIVERY="$(last_header_value "$TEMP_DIR/report.headers" X-Report-Share-Delivery)"
if [ "$REPORT_DELIVERY" = "backend-fallback" ]; then
    grep -Fq '<html lang="ko" data-share-delivery="backend-fallback">' \
        "$TEMP_DIR/report.body" || fail 'report fallback page marker is missing'
    grep -Fq '<meta property="og:title" content="싸방팔방 임장 리포트"' \
        "$TEMP_DIR/report.body" || fail 'report fallback page is missing the public OG title'
    grep -Fq "<meta property=\"og:url\" content=\"$HTTPS_BASE_URL/report/1\"" \
        "$TEMP_DIR/report.body" || fail 'report fallback page has an unexpected OG URL'
    grep -Fq 'href="intent://portfolio.example.com/open/report/1#Intent;scheme=https;package=com.ssafy.ssabangpalbang;end"' \
        "$TEMP_DIR/report.body" || fail 'report fallback page is missing the explicit Android app intent'
    if grep -Fq '/api/v1/reports/1' "$TEMP_DIR/report.body"; then
        fail 'report fallback page must not fetch or expose report data'
    fi

    REPORT_ROBOTS_HEADER="$(last_header_value "$TEMP_DIR/report.headers" X-Robots-Tag)"
    [ "$REPORT_ROBOTS_HEADER" = 'noindex, nofollow, noarchive' ] || \
        fail "unexpected report fallback X-Robots-Tag: ${REPORT_ROBOTS_HEADER:-none}"
    REPORT_CSP_HEADER="$(last_header_value "$TEMP_DIR/report.headers" Content-Security-Policy)"
    case "$REPORT_CSP_HEADER" in
        *"frame-ancestors 'none'"*) ;;
        *) fail 'report fallback page is missing frame-ancestors protection' ;;
    esac

    for INVALID_REPORT_PATH in \
        /report/0 \
        /report/-1 \
        /report/not-a-number \
        /report/9007199254740992
    do
        INVALID_REPORT_STATUS="$(
            curl \
                --path-as-is \
                --silent \
                --show-error \
                --max-time 10 \
                --output /dev/null \
                --write-out '%{http_code}' \
                "$HTTPS_BASE_URL$INVALID_REPORT_PATH"
        )"
        [ "$INVALID_REPORT_STATUS" = "404" ] || \
            fail "expected $INVALID_REPORT_PATH HTTP 404, received $INVALID_REPORT_STATUS"
    done
else

grep -Fq '<meta property="og:title" content="싸방팔방 임장 리포트"' \
    "$TEMP_DIR/report.body" || fail 'report share page is missing the public OG title'
grep -Fq 'content="https://portfolio.example.com/share/report-card.png"' \
    "$TEMP_DIR/report.body" || fail 'report share page is missing the absolute OG image'
grep -Fq "<meta property=\"og:url\" content=\"$HTTPS_BASE_URL/report/1\"" \
    "$TEMP_DIR/report.body" || fail 'report share page has an unexpected OG URL'
grep -Fq "href=\"$HTTPS_BASE_URL/open/report/1\"" \
    "$TEMP_DIR/report.body" || fail 'report share page is missing the separated App Link'
tr '\n' ' ' < "$TEMP_DIR/report.body" |
    grep -Eq 'id="install-app"[[:space:]]+href="https://[^"[:space:]]+"' || \
    fail 'report share page is missing the HTTPS install CTA'
grep -Fq 'href="/share/report.webmanifest"' \
    "$TEMP_DIR/report.body" || fail 'report share page is missing the web manifest'
grep -Fq 'src="/share/report-open.js"' \
    "$TEMP_DIR/report.body" || fail 'report share page is missing installed-app detection'
if grep -Fq 'ssabangpalbang://report/' "$TEMP_DIR/report.body"; then
    fail 'report share page must not use the custom scheme for its app-open CTA'
fi
grep -Fq '<meta name="robots" content="noindex, nofollow, noarchive"' \
    "$TEMP_DIR/report.body" || fail 'report share page is missing crawler exclusion metadata'

REPORT_ROBOTS_HEADER="$(last_header_value "$TEMP_DIR/report.headers" X-Robots-Tag)"
[ "$REPORT_ROBOTS_HEADER" = 'noindex, nofollow, noarchive' ] || \
    fail "unexpected report X-Robots-Tag: ${REPORT_ROBOTS_HEADER:-none}"
REPORT_CSP_HEADER="$(last_header_value "$TEMP_DIR/report.headers" Content-Security-Policy)"
case "$REPORT_CSP_HEADER" in
    *"frame-ancestors 'none'"*) ;;
    *) fail 'report share page is missing frame-ancestors protection' ;;
esac
case "$REPORT_CSP_HEADER" in
    *"manifest-src 'self'"*"script-src 'self'"*) ;;
    *) fail 'report share page CSP does not allow its manifest and script' ;;
esac

echo "===== REPORT APP-OPEN WEB FALLBACK ====="
OPEN_REPORT_STATUS="$(
    curl \
        --path-as-is \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/open-report.headers" \
        --output /dev/null \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/open/report/1?next=https://evil.example/ignored"
)"

[ "$OPEN_REPORT_STATUS" = "302" ] || \
    fail "expected App Link web fallback HTTP 302, received $OPEN_REPORT_STATUS"
OPEN_REPORT_LOCATION="$(last_location "$TEMP_DIR/open-report.headers")"
[ "$OPEN_REPORT_LOCATION" = "$HTTPS_BASE_URL/report/1?install=1" ] || \
    fail "unexpected App Link web fallback location: ${OPEN_REPORT_LOCATION:-none}"

for INVALID_REPORT_PATH in \
    /report/0 \
    /report/-1 \
    /report/not-a-number \
    /report//1 \
    /report/1/ \
    /report/1/extra \
    /report/9007199254740992 \
    /open/report/0 \
    /open/report/-1 \
    /open/report/not-a-number \
    /open/report//1 \
    /open/report/1/ \
    /open/report/1/extra \
    /open/report/9007199254740992
do
    INVALID_REPORT_STATUS="$(
        curl \
            --path-as-is \
            --silent \
            --show-error \
            --max-time 10 \
            --output /dev/null \
            --write-out '%{http_code}' \
            "$HTTPS_BASE_URL$INVALID_REPORT_PATH"
    )"
    [ "$INVALID_REPORT_STATUS" = "404" ] || \
        fail "expected $INVALID_REPORT_PATH HTTP 404, received $INVALID_REPORT_STATUS"
done

echo "===== REPORT LANDING STATIC ASSETS ====="
REPORT_MANIFEST_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/report-manifest.headers" \
        --output "$TEMP_DIR/report-manifest.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/share/report.webmanifest"
)"

[ "$REPORT_MANIFEST_STATUS" = "200" ] || \
    fail "expected report web manifest HTTP 200, received $REPORT_MANIFEST_STATUS"
REPORT_MANIFEST_CONTENT_TYPE="$(
    last_header_value "$TEMP_DIR/report-manifest.headers" Content-Type
)"
case "$(printf '%s' "$REPORT_MANIFEST_CONTENT_TYPE" | tr '[:upper:]' '[:lower:]')" in
    application/manifest+json*) ;;
    *) fail "unexpected report web manifest Content-Type: ${REPORT_MANIFEST_CONTENT_TYPE:-none}" ;;
esac
REPORT_MANIFEST_COMPACT="$(tr -d '[:space:]' < "$TEMP_DIR/report-manifest.body")"
case "$REPORT_MANIFEST_COMPACT" in
    *'"related_applications":[{"platform":"play","id":"com.ssafy.ssabangpalbang"}]'*) ;;
    *) fail 'report web manifest is missing the Android related application' ;;
esac

REPORT_SCRIPT_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/report-script.headers" \
        --output "$TEMP_DIR/report-script.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/share/report-open.js"
)"

[ "$REPORT_SCRIPT_STATUS" = "200" ] || \
    fail "expected report landing script HTTP 200, received $REPORT_SCRIPT_STATUS"
REPORT_SCRIPT_CONTENT_TYPE="$(
    last_header_value "$TEMP_DIR/report-script.headers" Content-Type
)"
case "$(printf '%s' "$REPORT_SCRIPT_CONTENT_TYPE" | tr '[:upper:]' '[:lower:]')" in
    application/javascript*) ;;
    *) fail "unexpected report landing script Content-Type: ${REPORT_SCRIPT_CONTENT_TYPE:-none}" ;;
esac
grep -Fq 'getInstalledRelatedApps' "$TEMP_DIR/report-script.body" || \
    fail 'report landing script is missing installed-app detection'
grep -Fq 'showModal' "$TEMP_DIR/report-script.body" || \
    fail 'report landing script is missing the confirmation dialog'

echo "===== APP LINK ASSOCIATION ====="
ASSETLINKS_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/assetlinks.headers" \
        --output "$TEMP_DIR/assetlinks.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/.well-known/assetlinks.json"
)"

[ "$ASSETLINKS_STATUS" = "200" ] || \
    fail "expected assetlinks HTTP 200, received $ASSETLINKS_STATUS"

ASSETLINKS_CONTENT_TYPE="$(
    last_header_value "$TEMP_DIR/assetlinks.headers" Content-Type
)"
case "$(printf '%s' "$ASSETLINKS_CONTENT_TYPE" | tr '[:upper:]' '[:lower:]')" in
    application/json*) ;;
    *) fail "unexpected assetlinks Content-Type: ${ASSETLINKS_CONTENT_TYPE:-none}" ;;
esac

ASSETLINKS_FINGERPRINTS="$(
    grep -Eo '([[:xdigit:]]{2}:){31}[[:xdigit:]]{2}' \
        "$TEMP_DIR/assetlinks.body" || true
)"
ASSETLINKS_FINGERPRINT_COUNT="$(
    printf '%s\n' "$ASSETLINKS_FINGERPRINTS" |
        awk 'NF { count += 1 } END { print count + 0 }'
)"
[ "$ASSETLINKS_FINGERPRINT_COUNT" = '1' ] || \
    fail "assetlinks must contain exactly one release fingerprint, found $ASSETLINKS_FINGERPRINT_COUNT"

DEPLOYED_APP_LINK_SHA256="$(
    printf '%s' "$ASSETLINKS_FINGERPRINTS" | tr '[:lower:]' '[:upper:]'
)"
ASSETLINKS_COMPACT="$(tr -d '[:space:]' < "$TEMP_DIR/assetlinks.body")"
EXPECTED_ASSETLINKS_COMPACT='[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"com.ssafy.ssabangpalbang","sha256_cert_fingerprints":["'"$DEPLOYED_APP_LINK_SHA256"'"]}}]'
[ "$ASSETLINKS_COMPACT" = "$EXPECTED_ASSETLINKS_COMPACT" ] || \
    fail 'assetlinks does not match the exact Android association JSON contract'

if [ -n "$EXPECTED_APP_LINK_SHA256" ]; then
    EXPECTED_APP_LINK_SHA256="$(
        printf '%s' "$EXPECTED_APP_LINK_SHA256" | tr '[:lower:]' '[:upper:]'
    )"
    printf '%s\n' "$EXPECTED_APP_LINK_SHA256" | \
        grep -Eq '^([0-9A-F]{2}:){31}[0-9A-F]{2}$' || \
        fail 'EXPECTED_APP_LINK_SHA256 has an invalid format'
    [ "$DEPLOYED_APP_LINK_SHA256" = "$EXPECTED_APP_LINK_SHA256" ] || \
        fail 'assetlinks fingerprint does not match EXPECTED_APP_LINK_SHA256'
fi

REPORT_IMAGE_STATUS="$(
    curl \
        --silent \
        --show-error \
        --max-time 10 \
        --dump-header "$TEMP_DIR/report-image.headers" \
        --output "$TEMP_DIR/report-image.body" \
        --write-out '%{http_code}' \
        "$HTTPS_BASE_URL/share/report-card.png"
)"

[ "$REPORT_IMAGE_STATUS" = "200" ] || \
    fail "expected report image HTTP 200, received $REPORT_IMAGE_STATUS"
REPORT_IMAGE_CONTENT_TYPE="$(
    last_header_value "$TEMP_DIR/report-image.headers" Content-Type
)"
case "$(printf '%s' "$REPORT_IMAGE_CONTENT_TYPE" | tr '[:upper:]' '[:lower:]')" in
    image/png*) ;;
    *) fail "unexpected report image Content-Type: ${REPORT_IMAGE_CONTENT_TYPE:-none}" ;;
esac
REPORT_IMAGE_SIGNATURE="$(
    od -An -tx1 -N8 "$TEMP_DIR/report-image.body" | tr -d '[:space:]'
)"
[ "$REPORT_IMAGE_SIGNATURE" = '89504e470d0a1a0a' ] || \
    fail 'report image response does not contain PNG bytes'
fi
else
    echo "WARNING: Report App Link detailed checks skipped after expected HTTP 401."
fi

echo "===== WEBSOCKET HANDSHAKE ====="
set +e
curl \
    --http1.1 \
    --silent \
    --show-error \
    --max-time 5 \
    --dump-header "$TEMP_DIR/websocket.headers" \
    --output /dev/null \
    --header 'Connection: Upgrade' \
    --header 'Upgrade: websocket' \
    --header 'Sec-WebSocket-Version: 13' \
    --header "Sec-WebSocket-Key: $WEBSOCKET_KEY" \
    "$HTTPS_BASE_URL/ws"
WEBSOCKET_CURL_STATUS=$?
set -e

WEBSOCKET_STATUS="$(last_http_status "$TEMP_DIR/websocket.headers")"
[ "$WEBSOCKET_STATUS" = "101" ] || \
    fail "expected WebSocket HTTP 101, received ${WEBSOCKET_STATUS:-none} (curl=$WEBSOCKET_CURL_STATUS)"

WEBSOCKET_UPGRADE="$(
    last_header_value "$TEMP_DIR/websocket.headers" Upgrade
)"
WEBSOCKET_CONNECTION="$(
    last_header_value "$TEMP_DIR/websocket.headers" Connection
)"
WEBSOCKET_ACCEPT="$(
    last_header_value "$TEMP_DIR/websocket.headers" Sec-WebSocket-Accept
)"

[ "$(printf '%s' "$WEBSOCKET_UPGRADE" | tr '[:upper:]' '[:lower:]')" = \
    "websocket" ] || fail "missing WebSocket Upgrade response header"
[ "$(printf '%s' "$WEBSOCKET_CONNECTION" | tr '[:upper:]' '[:lower:]')" = \
    "upgrade" ] || fail "missing WebSocket Connection response header"
[ "$WEBSOCKET_ACCEPT" = "$EXPECTED_WEBSOCKET_ACCEPT" ] || \
    fail "invalid WebSocket Sec-WebSocket-Accept response header"

if [ "$EXPECTED_REPORT_STATUS" = "401" ]; then
    echo "PRODUCTION SMOKE TEST PASSED WITH REPORT APP LINK CHECKS SKIPPED"
else
    echo "PRODUCTION SMOKE TEST PASSED"
fi

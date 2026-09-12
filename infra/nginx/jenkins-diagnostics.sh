#!/bin/sh
set -u

DOMAIN="portfolio.example.com"
CERTIFICATE="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"
ACCESS_LOG="/var/log/nginx/ssabangpalbang-access.log"
ERROR_LOG="/var/log/nginx/ssabangpalbang-error.log"

echo "===== NGINX CONFIGURATION ====="
/usr/sbin/nginx -t 2>&1 || true

echo "===== NGINX SERVICE ====="
/usr/bin/systemctl \
    --no-pager \
    --property=LoadState \
    --property=ActiveState \
    --property=SubState \
    --property=UnitFileState \
    show nginx 2>&1 || true

echo "===== HTTPS CERTIFICATE ====="
if [ -r "$CERTIFICATE" ]; then
    /usr/bin/openssl x509 \
        -in "$CERTIFICATE" \
        -noout \
        -subject \
        -issuer \
        -dates 2>&1 || true
else
    echo "Certificate is not readable: $CERTIFICATE"
fi

echo "===== NGINX ERROR LOG ====="
if [ -r "$ERROR_LOG" ]; then
    /usr/bin/tail -n 200 "$ERROR_LOG" 2>&1 \
        | /usr/bin/sed -E \
            -e 's#\?[^[:space:]"\\]+#?[REDACTED]#g' \
            -e 's#Bearer[[:space:]]+[A-Za-z0-9._~+/=-]+#Bearer [REDACTED]#g' \
        || true
else
    echo "Nginx error log is not readable: $ERROR_LOG"
fi

echo "===== NGINX SAFE ACCESS LOG ====="
if [ -r "$ACCESS_LOG" ]; then
    /usr/bin/tail -n 100 "$ACCESS_LOG" 2>&1 || true
else
    echo "Nginx access log is not readable: $ACCESS_LOG"
fi

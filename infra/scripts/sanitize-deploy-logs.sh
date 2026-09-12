#!/usr/bin/env bash
set -Eeuo pipefail

SENSITIVE_ENV_FILE="${PROD_ENV_FILE:-}"

redact_known_secrets() {
    if [ -n "$SENSITIVE_ENV_FILE" ] && [ -r "$SENSITIVE_ENV_FILE" ]; then
        awk -v secret_file="$SENSITIVE_ENV_FILE" '
            function trim(value) {
                sub(/\r$/, "", value)
                sub(/^[[:space:]]+/, "", value)
                sub(/[[:space:]]+$/, "", value)
                return value
            }

            function parse_env_value(value, quote, closing) {
                value = trim(value)
                quote = substr(value, 1, 1)

                if (quote == "\"" || quote == sprintf("%c", 39)) {
                    value = substr(value, 2)
                    closing = index(value, quote)
                    if (closing > 0) {
                        value = substr(value, 1, closing - 1)
                    }
                } else {
                    sub(/[[:space:]]+#.*$/, "", value)
                }

                return value
            }

            function add_secret(value, first, last, quote) {
                value = trim(value)
                first = substr(value, 1, 1)
                last = substr(value, length(value), 1)
                quote = sprintf("%c", 39)

                if (length(value) >= 2 && ((first == "\"" && last == "\"") || (first == quote && last == quote))) {
                    value = substr(value, 2, length(value) - 2)
                }

                if (length(value) > 0 && !seen[value]++) {
                    secrets[++secret_count] = value
                }
            }

            function replace_literal(text, needle, replacement, position, output) {
                if (needle == "") {
                    return text
                }

                output = ""
                while ((position = index(text, needle)) > 0) {
                    output = output substr(text, 1, position - 1) replacement
                    text = substr(text, position + length(needle))
                }
                return output text
            }

            BEGIN {
                add_secret(ENVIRON["JWT_SECRET"])
                add_secret(ENVIRON["MEDIA_GATEWAY_INTERNAL_TOKEN"])
                add_secret(ENVIRON["POSTGRES_PASSWORD"])
                add_secret(ENVIRON["REDIS_PASSWORD"])
                add_secret(ENVIRON["AI_REPORT_DB_PASSWORD"])
            }

            FILENAME == secret_file {
                if ($0 ~ /^[[:space:]]*(#|$)/) {
                    next
                }

                separator = index($0, "=")
                if (separator == 0) {
                    next
                }

                key = toupper(trim(substr($0, 1, separator - 1)))
                value = substr($0, separator + 1)

                if (key ~ /(PASSWORD|SECRET|TOKEN|API_KEY|PRIVATE_KEY|ACCESS_KEY)$/) {
                    add_secret(parse_env_value(value))
                }
                next
            }

            {
                line = $0
                for (index_value = 1; index_value <= secret_count; index_value++) {
                    line = replace_literal(line, secrets[index_value], "[REDACTED]")
                }
                print line
            }
        ' "$SENSITIVE_ENV_FILE" -
    else
        cat
    fi
}

is_jwt_header() {
    local encoded_header="$1"
    local padding=""

    case $((${#encoded_header} % 4)) in
        0) ;;
        2) padding="==" ;;
        3) padding="=" ;;
        *) return 1 ;;
    esac

    printf '%s' "${encoded_header}${padding}" \
        | tr '_-' '/+' \
        | base64 -d 2>/dev/null \
        | tr -d '\r\n' \
        | LC_ALL=C grep -aqE '^[[:space:]]*\{.*"alg"[[:space:]]*:'
}

redact_jwt_candidates() {
    local line=""
    local remainder=""
    local output=""
    local matched=""
    local before=""
    local prefix=""
    local candidate=""
    local suffix=""
    local header=""

    while IFS= read -r line || [ -n "$line" ]; do
        remainder="$line"
        output=""

        while [[ "$remainder" =~ (^|[^A-Za-z0-9_-])([A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,})([^A-Za-z0-9_-]|$) ]]; do
            matched="${BASH_REMATCH[0]}"
            prefix="${BASH_REMATCH[1]}"
            candidate="${BASH_REMATCH[2]}"
            suffix="${BASH_REMATCH[3]}"
            before="${remainder%%"$matched"*}"
            header="${candidate%%.*}"

            if is_jwt_header "$header"; then
                output="${output}${before}${prefix}[JWT REDACTED]${suffix}"
            else
                output="${output}${before}${matched}"
            fi
            remainder="${remainder#*"$matched"}"
        done

        printf '%s%s\n' "$output" "$remainder"
    done
}

redact_known_secrets \
    | redact_jwt_candidates \
    | sed -E \
        -e '/-----BEGIN .*PRIVATE KEY-----/,/-----END .*PRIVATE KEY-----/c\[PRIVATE KEY REDACTED]' \
        -e '/(authorization|(set-)?cookie|password|secret|token|api[_-]?key|client[_-]?secret|private[_-]?key|access[_-]?key|session)[[:space:]\"]*:[[:space:]]*$/I { n; s#.*#[REDACTED]#; }' \
        -e "s#((authorization|(set-)?cookie|password|secret|token|api[_-]?key|client[_-]?secret|private[_-]?key|access[_-]?key|session)[\"']?[[:space:]]*[:=][[:space:]]*)[\"'][^\"']*[\"']#\1[REDACTED]#gI" \
        -e 's#((authorization|(set-)?cookie)[[:space:]]*[:=][[:space:]]*)[^,}]+#\1[REDACTED]#gI' \
        -e 's#((password|secret|token|api[_-]?key|client[_-]?secret|private[_-]?key|access[_-]?key|session)[[:space:]]*[:=][[:space:]]*)[^,}[:space:]\"]+#\1[REDACTED]#gI' \
        -e 's#[Bb]earer[[:space:]]+[A-Za-z0-9._~+/=-]+#Bearer [REDACTED]#gI' \
        -e 's#(((postgres(ql)?|redis(s)?|mysql|mongodb(\+srv)?)://)[^:/@[:space:]]+:)[^@[:space:]]+@#\1[REDACTED]@#gI' \
        -e 's#((https?://|/)[^?[:space:]\",]+)\?[^[:space:]\",}]+#\1?[REDACTED]#gI'

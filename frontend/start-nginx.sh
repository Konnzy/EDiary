#!/bin/sh
set -eu
export BACKEND_HOST="${BACKEND_HOST:-backend}"
export BACKEND_PORT="${BACKEND_PORT:-8081}"
case "$BACKEND_HOST" in *[!a-zA-Z0-9.-]*|'') echo 'Invalid BACKEND_HOST' >&2; exit 1;; esac
case "$BACKEND_PORT" in *[!0-9]*|'') echo 'Invalid BACKEND_PORT' >&2; exit 1;; esac
if [ "$BACKEND_PORT" -lt 1 ] || [ "$BACKEND_PORT" -gt 65535 ]; then exit 1; fi
NGINX_RESOLVER=$(awk '/^nameserver / { if ($2 ~ /:/) print "[" $2 "]"; else print $2; exit }' /etc/resolv.conf)
export NGINX_RESOLVER
test -n "$NGINX_RESOLVER"
envsubst '${BACKEND_HOST} ${BACKEND_PORT} ${NGINX_RESOLVER}' < /opt/ediary/nginx.conf.template > /tmp/ediary-default.conf
sed 's|/etc/nginx/conf.d/\*.conf|/tmp/ediary-default.conf|' /etc/nginx/nginx.conf > /tmp/ediary-nginx.conf
nginx -t -c /tmp/ediary-nginx.conf
exec nginx -c /tmp/ediary-nginx.conf -g 'daemon off;'

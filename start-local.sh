#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
export CLOUDSHIELD_MODE=local
export CLOUDSHIELD_DATA_DIR="${CLOUDSHIELD_DATA_DIR:-data}"
printf '%s\n' 'CloudShield: http://127.0.0.1:8080' 'Login: admin / CloudShield-Demo-2026!'
exec java -Xms128m -Xmx512m -jar web/target/cloudshield-web-1.0.0.jar

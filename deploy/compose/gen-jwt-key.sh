#!/usr/bin/env sh
# Generates the RS256 signing key for identity-service (dev / compose only).
#   sh deploy/compose/gen-jwt-key.sh        → deploy/compose/secrets/jwt-private.pem
# secrets/ is gitignored: NEVER commit the key. Re-running keeps the existing key unless you pass --force.
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)/secrets"
KEY="$DIR/jwt-private.pem"
mkdir -p "$DIR"
if [ -f "$KEY" ] && [ "${1:-}" != "--force" ]; then
  echo "Key already exists: $KEY (use --force to replace it; every issued token becomes invalid)"
  exit 0
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEY"   # PKCS#8 PEM
# Readable by the non-root user inside the container (bind mount keeps host permissions). Dev key only.
chmod 644 "$KEY"
echo "Wrote $KEY"

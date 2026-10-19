#!/usr/bin/env bash
# Usage:
#   ./starttemporal.sh                      # no codec server
#   ./starttemporal.sh codec                # codec server at http://localhost:8080/codec
#   ./starttemporal.sh http://host:port     # codec server at a custom endpoint

set -euo pipefail

if [ $# -eq 0 ]; then
  echo "Starting Temporal dev server (no codec server)"
  exec temporal server start-dev
fi

if [ "$1" = "codec" ]; then
  CODEC_ENDPOINT="http://localhost:8080/codec"
else
  CODEC_ENDPOINT="$1"
fi

echo "Starting Temporal dev server with codec endpoint: $CODEC_ENDPOINT"
exec temporal server start-dev --ui-codec-endpoint "$CODEC_ENDPOINT"
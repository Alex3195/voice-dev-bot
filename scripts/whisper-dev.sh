#!/usr/bin/env bash
# Dev uchun native whisper.cpp server (macOS'da Metal GPU bilan — Docker'dan tezroq).
# Docker'dagi (compose.yaml) server bilan bir xil API va port: bot sozlamasi o'zgarmaydi.
#
# O'rnatish: brew install whisper-cpp ffmpeg
set -euo pipefail

MODELS_DIR="${WHISPER_MODELS_DIR:-$HOME/.local/share/whisper-models}"
# turbo tezroq, lekin o'zbekchada sezilarli zaif — to'liq large-v3 ishlatiladi
MODEL="${WHISPER_MODEL:-ggml-large-v3-q5_0.bin}"
PORT="${WHISPER_PORT:-8178}"
LANGUAGE="${STT_WHISPER_LANGUAGE:-uz}"

if [[ ! -f "$MODELS_DIR/$MODEL" ]]; then
  echo "Model topilmadi: $MODELS_DIR/$MODEL (README.md → 'Whisper modeli')" >&2
  exit 1
fi

# --convert vaqtinchalik .wav fayllarni joriy papkaga yozadi — repo ichida qolmasin
cd "${TMPDIR:-/tmp}"

exec whisper-server \
  --model "$MODELS_DIR/$MODEL" \
  --host 127.0.0.1 --port "$PORT" \
  --language "$LANGUAGE" \
  --beam-size 5 \
  --convert

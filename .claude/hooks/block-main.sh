#!/usr/bin/env bash
# PreToolUse (Bash): main branch'da git commit/push'ni bloklaydi.
# Permission pattern'lar joriy branch'ni bilmaydi, shuning uchun bu tekshiruv hook'da.
set -euo pipefail

command=$(jq -r '.tool_input.command // empty')

if ! grep -Eq '(^|[;&|[:space:]])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+(commit|push)([[:space:]]|$)' <<<"$command"; then
  exit 0
fi

branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" symbolic-ref --short HEAD 2>/dev/null || echo "")

if [[ "$branch" == "main" || "$branch" == "master" ]]; then
  echo "Bloklandi: '$branch' branch'da commit/push taqiqlangan (docs/agent-workflow.md). Avval feature/<nom> branch oching." >&2
  exit 2
fi

exit 0

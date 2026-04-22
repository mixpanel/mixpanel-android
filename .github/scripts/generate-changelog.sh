#!/bin/bash
set -euo pipefail

MODULE="$1"
VERSION_LABEL="$2"
REPO_URL="$3"
END_REF="${4:-HEAD}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULES_JSON="$SCRIPT_DIR/../modules.json"

TAG_PREFIX=$(jq -e -r --arg m "$MODULE" '.[$m].tag_prefix' "$MODULES_JSON") || {
  echo "Unknown module: $MODULE. Valid modules: $(jq -r 'keys | join(", ")' "$MODULES_JSON")" >&2
  exit 1
}
TAG_GLOB="${TAG_PREFIX}*"

PREVIOUS_TAG=$(git tag --sort=-creatordate --list "$TAG_GLOB" | head -1 || true)

if [ -z "$PREVIOUS_TAG" ]; then
  RANGE="$END_REF"
else
  RANGE="${PREVIOUS_TAG}..${END_REF}"
fi

DATE=$(date +%Y-%m-%d)
SAFE_URL=$(printf '%s' "$REPO_URL" | sed 's|[&/\]|\\&|g')

declare -a FEATURES=()
declare -a FIXES=()
declare -a CHORES=()

while IFS= read -r line; do
  [ -z "$line" ] && continue
  MSG=$(echo "$line" | cut -d' ' -f2-)

  if [[ "$MSG" =~ ^(feat|fix|chore)\((${MODULE}|all)\):\ (.+) ]]; then
    TYPE="${BASH_REMATCH[1]}"
    DESC="${BASH_REMATCH[3]}"

    DESC=$(echo "$DESC" | sed -E "s|\(#([0-9]+)\)|([#\1](${SAFE_URL}/pull/\1))|g")

    case "$TYPE" in
      feat) FEATURES+=("$DESC") ;;
      fix) FIXES+=("$DESC") ;;
      chore) CHORES+=("$DESC") ;;
    esac
  fi
done < <(git log --oneline "$RANGE")

echo "## [${VERSION_LABEL}](${REPO_URL}/tree/${VERSION_LABEL}) (${DATE})"
echo ""

if [ ${#FEATURES[@]} -gt 0 ]; then
  echo "### Features"
  for entry in "${FEATURES[@]}"; do
    echo "- ${entry}"
  done
  echo ""
fi

if [ ${#FIXES[@]} -gt 0 ]; then
  echo "### Fixes"
  for entry in "${FIXES[@]}"; do
    echo "- ${entry}"
  done
  echo ""
fi

if [ ${#CHORES[@]} -gt 0 ]; then
  echo "### Chores"
  for entry in "${CHORES[@]}"; do
    echo "- ${entry}"
  done
  echo ""
fi

if [ -n "$PREVIOUS_TAG" ]; then
  echo "[Full Changelog](${REPO_URL}/compare/${PREVIOUS_TAG}...${VERSION_LABEL})"
fi

#!/usr/bin/env bash
# Publishes the 2.1.0 train work packages (docs/tasks/2.1.0/*.md) as GitHub issues.
#
# Prerequisites:
#   - gh CLI installed and authenticated:  https://cli.github.com  (gh auth login)
#   - run from the repository root
#   - optional: a repo milestone named 2.1.0 (issues get attached only if it exists)
#
# Idempotent: an issue is skipped when an issue with the exact same title already exists
# (open or closed). Labels are created if missing.
set -euo pipefail

DIR="docs/tasks/2.1.0"
MILESTONE="2.1.0"

command -v gh >/dev/null 2>&1 || { echo "gh CLI not installed - see https://cli.github.com"; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "run from the repository root"; exit 1; }
[ -d "$DIR" ] || { echo "$DIR not found"; exit 1; }

# --- 1. Labels (idempotent) -----------------------------------------------------------
label() { gh label create "$1" --color "$2" --description "$3" >/dev/null 2>&1 || true; }
label "2.1.0-train" "1D76DB" "Work package of the 2.1.0 release train"
label "P0"          "B60205" "Must land in 2.1.0"
label "P1"          "D93F0B" "Should land in 2.1.0"
label "P2"          "0E8A16" "Nice to have in 2.1.0"
label "logging"     "5319E7" "Logging subsystem"
label "testing"     "2374CA" "Test estate: coverage, grouping, suites"
label "engine"      "F9D0C4" "Storage engine internals"
label "search"      "C2E0C6" "Search engine / query model"
label "docs"        "0075CA" "Documentation"
label "ci"          "E99695" "Build / CI / pipelines"
label "refactor"    "FBCA04" "Restructure without behavior change"
label "feature"     "A2EEEF" "New capability"
label "chore"       "CCCCCC" "Maintenance"
label "tech-debt"   "D4C5F9" "Known debt to pay down"
label "performance" "FEF2C0" "Performance work"

# --- 2. Milestone (attached only if it exists) ----------------------------------------
MILESTONE_ARG=()
if gh api "repos/:owner/:repo/milestones" --jq '.[].title' 2>/dev/null | grep -qx "$MILESTONE"; then
  MILESTONE_ARG=(--milestone "$MILESTONE")
  echo "milestone found: $MILESTONE"
else
  echo "note: no milestone '$MILESTONE' - issues published without one"
fi

# --- 3. Issues -------------------------------------------------------------------------
created=0; skipped=0
for f in "$DIR"/[0-9][0-9]-*.md; do
  [ -f "$f" ] || continue
  title=$(sed -n 's/^title: "\(.*\)"$/\1/p' "$f")
  labels=$(sed -n 's/^labels: \[\(.*\)\]$/\1/p' "$f" | tr -d ' ')
  if [ -z "$title" ] || [ -z "$labels" ]; then
    echo "WARN: $f - missing/invalid frontmatter (title/labels), skipped"; continue
  fi
  if gh issue list --state all --json title --jq '.[].title' | grep -Fxq "$title"; then
    echo "skip (exists): $title"; skipped=$((skipped+1)); continue
  fi
  body=$(sed '1{/^---$/d}' "$f" | sed '1,/^---$/d')
  tmp=$(mktemp); printf '%s\n' "$body" > "$tmp"
  # shellcheck disable=SC2086
  if gh issue create --title "$title" --body-file "$tmp" --label "$labels" "${MILESTONE_ARG[@]}"; then
    echo "created: $title"; created=$((created+1))
  else
    echo "FAILED: $title"; rm -f "$tmp"; exit 1
  fi
  rm -f "$tmp"
done

echo "done: $created created, $skipped skipped"

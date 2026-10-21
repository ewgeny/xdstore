#!/usr/bin/env bash
# Idempotent GitHub-issue sync for the 2.1.0 train work packages (docs/tasks/2.1.0).
#
# Source of truth: each task file's frontmatter
#   title:  issue title (exact-match key, open or closed)
#   labels: [a, b, c]    applied at creation
#   status: open | done  <- the DEVELOPER sets 'done' when the task is finished;
#                          the next sync closes the matching GitHub issue.
#
# State machine per task file (every case idempotent):
#   status=open,  no issue      -> CREATE the issue (open)
#   status=open,  issue open    -> nothing (in sync)
#   status=open,  issue closed  -> WARN (left closed; a machine never reopens)
#   status=done,  no issue      -> CREATE, then CLOSE with an explanatory comment
#   status=done,  issue open    -> CLOSE with an explanatory comment
#   status=done,  issue closed  -> nothing (in sync)
#
# Runs automatically via .github/workflows/issues-sync.yml on every push to main,
# or locally:  gh auth login && bash scripts/sync-issues.sh [--dry-run]
set -euo pipefail

DIR="docs/tasks/2.1.0"
MILESTONE="2.1.0"
DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

command -v gh >/dev/null 2>&1 || { echo "gh CLI not installed - https://cli.github.com"; exit 1; }
[ -d "$DIR" ] || { echo "$DIR not found (run from the repository root)"; exit 1; }

# Token: gh reads GH_TOKEN; XDSTORAGE_ISSUES_TOKEN (the same secret the issues-sync
# workflow uses) is accepted as a fallback for local runs. Precedence: explicit
# GH_TOKEN > XDSTORAGE_ISSUES_TOKEN > gh's own stored auth (gh auth login).
if [ -z "${GH_TOKEN:-}" ] && [ -n "${XDSTORAGE_ISSUES_TOKEN:-}" ]; then
  export GH_TOKEN="$XDSTORAGE_ISSUES_TOKEN"
fi

created=0; closed=0; insync=0; warned=0
SUMMARY="| action | count |
|---|---|
"

# --- 1. Labels (idempotent) ------------------------------------------------------------
label() {
  [ "$DRY_RUN" = 1 ] && return 0
  gh label create "$1" --color "$2" --description "$3" >/dev/null 2>&1 || true
}
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

# --- 2. Milestone (attached at creation only if it exists) ------------------------------
MILESTONE_ARG=()
if [ "$DRY_RUN" != 1 ] && gh api "repos/:owner/:repo/milestones" --jq '.[].title' 2>/dev/null | grep -qx "$MILESTONE"; then
  MILESTONE_ARG=(--milestone "$MILESTONE")
fi

# --- 3. Existing issues, fetched ONCE (exact-title key) ---------------------------------
# Each element: "<number>\t<title>\t<STATE>"
if [ "$DRY_RUN" = 1 ]; then
  ISSUES=()
else
  mapfile -t ISSUES < <(gh issue list --state all --limit 1000 --json number,title,state \
    --jq '.[] | [(.number|tostring), .title, .state] | join("\t")')
fi
issue_lookup() { # $1 = exact title -> prints "<number> <STATE>", or returns 1 if absent
  printf '%s\n' ${ISSUES[@]+"${ISSUES[@]}"} | awk -F'\t' -v t="$1" '
    $2 == t { print $1, $3; found = 1; exit }
    END { if (!found) exit 1 }'
}

body_of() { # task file -> issue body (frontmatter stripped)
  sed '1{/^---$/d}' "$1" | sed '1,/^---$/d'
}

create_issue() { # $1 = file, $2 = title, $3 = comma labels -> prints issue number
  local tmp num
  tmp=$(mktemp)
  body_of "$1" > "$tmp"
  # shellcheck disable=SC2086
  num=$(gh issue create --title "$2" --body-file "$tmp" --label "$3" \
        ${MILESTONE_ARG[@]+"${MILESTONE_ARG[@]}"} | grep -oE '[0-9]+$')
  rm -f "$tmp"
  printf '%s' "$num"
}

close_issue() { # $1 = number, $2 = task file path
  gh issue close "$1" --reason completed --comment \
    "Automatically closed by the issues-sync workflow: the task file \`$2\` is marked \`status: done\` (the developer's completion mark)."
}

# --- 4. Per-file sync -------------------------------------------------------------------
for f in "$DIR"/[0-9][0-9]-*.md; do
  [ -f "$f" ] || continue
  title=$(sed -n 's/^title: "\(.*\)"$/\1/p' "$f")
  labels=$(sed -n 's/^labels: \[\(.*\)\]$/\1/p' "$f" | tr -d ' ')
  status=$(sed -n 's/^status: \(.*\)$/\1/p' "$f")
  status=${status:-open}
  if [ -z "$title" ] || [ -z "$labels" ]; then
    echo "WARN: $f - missing/invalid frontmatter (title/labels), skipped"
    warned=$((warned+1)); continue
  fi
  case "$status" in open|done) ;; *)
    echo "WARN: $f - invalid status '$status' (open|done), skipped"
    warned=$((warned+1)); continue ;;
  esac

  existing=$(issue_lookup "$title" || true)
  num=${existing%% *}
  state=${existing#* }

  if [ -z "$num" ]; then
    if [ "$DRY_RUN" = 1 ]; then
      echo "PLAN: create '$title' (status: $status)"
    else
      newnum=$(create_issue "$f" "$title" "$labels")
      echo "created #$newnum: $title"
      if [ "$status" = "done" ]; then
        close_issue "$newnum" "$f"
        echo "closed  #$newnum (status: done at creation)"
        closed=$((closed+1))
      fi
    fi
    created=$((created+1)); continue
  fi

  case "$state:$status" in
    OPEN:open|CLOSED:done)
      insync=$((insync+1)) ;;
    OPEN:done)
      if [ "$DRY_RUN" = 1 ]; then
        echo "PLAN: close #$num '$title' (status: done)"
      else
        close_issue "$num" "$f"
        echo "closed  #$num: $title (status: done)"
      fi
      closed=$((closed+1)) ;;
    CLOSED:open)
      echo "WARN: #$num '$title' is closed on GitHub but the file says status: open - left closed (a machine never reopens; fix the file or the issue by hand)"
      warned=$((warned+1)) ;;
  esac
done

echo "sync complete: $created created, $closed closed, $insync already in sync, $warned warnings"
SUMMARY="${SUMMARY}| created | ${created} |
| closed | ${closed} |
| already in sync | ${insync} |
| warnings | ${warned} |
"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '%s\n' "$SUMMARY" >> "${GITHUB_STEP_SUMMARY}"
fi

#!/usr/bin/env bash
# Puts a branch's build on the pre-release test-<branch>, which Settings > About > Test builds
# lists. Copied from Waypage's tools/test-build.sh (no iOS side).
#
#   bash tools/test-build.sh create ReTube_1.17.1-9-test.abc1234_*.apk
#
# Only an open pull request labelled test or test-android (or titled "manual testing") gets one.
# `create` remakes the pre-release (and its tag) at the pushed commit on every push. A pre-release
# is never GitHub's "latest", so the app's updater (releases/latest/download/newtube.json) never
# offers it. The notes start with the lines the app reads: version, code, branch, commit, pr.
# .github/workflows/test-builds.yml starts the build when the label goes on and removes the
# pre-release when the label goes, the PR closes or the branch is deleted.
# Needs GH_TOKEN, V (versionName), CODE (versionCode), GITHUB_SHA, GITHUB_REF_NAME, GITHUB_REPOSITORY.
set -euo pipefail
MODE="$1"; shift
BRANCH="${GITHUB_REF_NAME:?}"
TAG="test-$(printf '%s' "$BRANCH" | tr -c 'A-Za-z0-9._-' '-')"
PR="$(gh pr list --head "$BRANCH" --state open --json number,title,labels --jq '.[:1][] | "\(.number)\t\([.labels[].name] | join(","))\t\(.title)"' 2>/dev/null || true)"
if [ -z "$PR" ]; then echo "$BRANCH has no open pull request, so no test build"; exit 0; fi
NUMBER="${PR%%	*}"
REST="${PR#*	}"
LABELS=",${REST%%	*},"
TITLE="${REST#*	}"
BUILD=false
case "$LABELS" in *,test,*|*,test-android,*) BUILD=true ;; esac
# A title that says the change needs manual testing gets a build too, with no label. Read as text.
case "$(printf '%s' "$TITLE" | tr '[:upper:]' '[:lower:]')" in *"manual testing"*) BUILD=true ;; esac

case "$MODE" in
  create)
    if [ "$BUILD" != true ]; then echo "#$NUMBER isn't labelled test or test-android, so no test build"; exit 0; fi
    gh release delete "$TAG" --yes --cleanup-tag >/dev/null 2>&1 || true
    {
      echo "version: ${V:?}"
      echo "code: ${CODE:?}"
      echo "branch: $BRANCH"
      echo "commit: $GITHUB_SHA"
      echo "pr: $NUMBER"
      echo
      echo "A test build of pull request #$NUMBER, built from \`$BRANCH\` at $GITHUB_SHA. It isn't a release: ReTube's updater never offers it. In the app, Settings › About › Test builds installs it over the ReTube you have and keeps your data, and Back to the release there puts the released version back."
    } > test-notes.md
    gh release create "$TAG" "$@" --prerelease --target "$GITHUB_SHA" --title "Test: $TITLE" --notes-file test-notes.md
    echo "published $TAG for #$NUMBER at $GITHUB_SHA"
    ;;
  *) echo "usage: test-build.sh create <files...>"; exit 2 ;;
esac

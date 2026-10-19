#!/usr/bin/env bash
# Turns Kotlin/Gradle compiler errors from a build log into GitHub annotations so they show up
# on the check run (and through the checks API) without anyone having to download the log.
#
# usage: annotate-gradle-errors.sh <build.log>
log="${1:-build.log}"
if [ ! -f "$log" ]; then
  echo "::warning::no build log at $log"
  exit 0
fi

count=0
while IFS= read -r line; do
  count=$((count + 1))
  [ "$count" -gt 40 ] && break
  # e: file:///home/runner/work/<repo>/<repo>/app/src/main/java/.../File.kt:12:5 message
  if [[ "$line" =~ ^e:\ file://.*/(app/src/[^:]+):([0-9]+):([0-9]+)\ (.*)$ ]]; then
    echo "::error file=${BASH_REMATCH[1]},line=${BASH_REMATCH[2]},col=${BASH_REMATCH[3]}::${BASH_REMATCH[4]}"
  else
    echo "::error::${line}"
  fi
done < <(grep -E '^e: ' "$log")

# When there is no Kotlin error line, surface Gradle's own explanation of the failure.
if [ "$count" -eq 0 ]; then
  grep -E -A6 'What went wrong' "$log" | head -12 | while IFS= read -r l; do echo "::error::${l}"; done
fi
exit 0

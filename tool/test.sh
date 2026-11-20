#!/usr/bin/env bash
# Runs the Dart, Android and iOS test suites, or only those named:
#   tool/test.sh [dart] [android] [ios]
#
# Uses `flutter` from PATH; override with e.g. FLUTTER="fvm spawn 3.47.4".
# The iOS suite runs on the simulator named by IOS_SIMULATOR (default "iPhone 17").
#
# Lockfiles are never rewritten: if they don't fit this Flutter version, it stops.

# Sourced, `set -e` would close your shell on the first failure.
if [[ -n ${ZSH_VERSION:-} && ${ZSH_EVAL_CONTEXT:-} == *:file* ]] ||
  [[ -n ${BASH_VERSION:-} && ${BASH_SOURCE[0]} != "$0" ]]; then
  echo "Run tool/test.sh instead of sourcing it." >&2
  return 1
fi

set -euo pipefail

cd "$(dirname "$0")/.."
root=$PWD
flutter=(${FLUTTER:-flutter})
suites=("$@")
[ ${#suites[@]} -eq 0 ] && suites=(dart android ios)

# The example is resolved from inside example/: from the root, Flutter copies this
# plugin into ./build for SwiftPM, a copy of itself that doubles on every run.
if ! "${flutter[@]}" pub get --enforce-lockfile --no-example ||
  ! (cd example && "${flutter[@]}" pub get --enforce-lockfile); then
  echo "The lockfiles don't fit $("${flutter[@]}" --version | head -1)." >&2
  echo "Use the version they were resolved with, e.g. FLUTTER=\"fvm spawn 3.47.4\" tool/test.sh" >&2
  exit 1
fi

# Each suite is run as a condition, where `set -e` doesn't apply: chain with &&.
dart() {
  "${flutter[@]}" analyze --no-pub &&
    "${flutter[@]}" test --no-pub --reporter github
}

android() {
  # The Flutter-configured JDK, as the shell's JAVA_HOME may not match what Gradle needs.
  local jdk
  jdk=$("${flutter[@]}" config --list | sed -n 's/^ *jdk-dir: //p')
  [ -n "$jdk" ] && export JAVA_HOME=$jdk

  # The config-only build writes local.properties and the Gradle wrapper, which aren't tracked.
  cd example &&
    "${flutter[@]}" build apk --debug --config-only --no-pub &&
    cd android &&
    ./gradlew --quiet :flutter_phone_lib:testDebugUnitTest --rerun
}

ios() {
  cd example &&
    "${flutter[@]}" build ios --config-only --simulator --no-pub &&
    cd ios &&
    xcodebuild test \
      -workspace Runner.xcworkspace \
      -scheme Runner \
      -destination "platform=iOS Simulator,name=${IOS_SIMULATOR:-iPhone 17}" 2>&1 |
    # Only the test results and errors, not the thousands of lines of build log.
    grep -E "^Test Case .* (passed|failed)|error:|\*\* TEST"
}

results=()
status=0
for suite in "${suites[@]}"; do
  case $suite in
    dart | android | ios) ;;
    *)
      echo "Unknown suite: $suite (expected dart, android or ios)" >&2
      exit 64
      ;;
  esac

  if [ "$suite" = ios ] && [ "$(uname)" != Darwin ]; then
    results+=("skipped  ios (needs macOS)")
    continue
  fi

  echo
  echo "==> $suite"
  if (cd "$root" && "$suite"); then
    results+=("passed   $suite")
  else
    results+=("FAILED   $suite (see its output above)")
    status=1
  fi
done

echo
echo "==> Results"
printf '  %s\n' "${results[@]}"
exit $status

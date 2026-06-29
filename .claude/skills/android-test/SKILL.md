---
name: android-test
description: Build the Android app and run its test suites (unit + instrumented) via Gradle, then report pass/fail results and surface failures. Use when verifying Android changes, running tests, or checking a build before committing.
allowed-tools: Bash, Read, Grep, Glob
---

# Android Test & Build

Verify the Android project builds and its tests pass, and report the results clearly.

## Steps

1. **Locate the Gradle wrapper.** Look for `./gradlew` at the repo root. If it is missing, the project has not been scaffolded yet — stop and tell the user there is no Gradle build to run.

2. **Pick the right tasks** based on what changed (use `git diff --name-only` to scope it):
   - Fast feedback / pure logic changes: `./gradlew testDebugUnitTest`
   - Static analysis (if configured): `./gradlew lint detekt ktlintCheck` — skip any task the project does not define.
   - Instrumented / UI / E2E changes: `./gradlew connectedDebugAndroidTest` (requires a running emulator or connected device; if none is available, say so and run the unit tests only).
   - Full check before a PR: `./gradlew build` or `./gradlew check`.

3. **Run with readable output:** prefer `./gradlew <tasks> --console=plain`.

4. **On failure**, open the relevant report under `app/build/reports/` (e.g. `tests/`, `androidTests/`, `lint-results-*.html`) or read the failing test source to explain the root cause — do not just echo the stack trace.

5. **Report** which tasks ran, the result of each, and a concise summary of any failures with the file/line to look at next.

## Constraints
- Do not edit code or "fix" tests from this skill — it verifies, it does not change behavior. Report failures and let the engineer decide.
- Never weaken or delete a test to make the build pass.
- If a device/emulator is required but unavailable, state that explicitly rather than reporting a false pass.

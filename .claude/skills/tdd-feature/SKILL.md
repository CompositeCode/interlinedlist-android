---
name: tdd-feature
description: Drive a new Android feature test-first — clarify the behavior, write failing unit/UI tests, then implement the minimum code to pass, applying SOLID/KISS/YAGNI. Use when starting a new feature or behavior change that should be built with TDD/BDD.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, TodoWrite
---

# TDD Feature

Build a feature test-first, in small red→green→refactor loops, keeping the design simple.

## Steps

1. **Pin down the behavior.** Restate the feature as concrete, testable acceptance criteria (Given/When/Then). If any criterion is ambiguous, ask before writing code.

2. **Survey the ground.** Read the relevant screens, view models, domain logic, and existing tests so the new work fits the current architecture and test conventions. Note the test frameworks in use (e.g. JUnit, Truth/AssertJ, MockK, Turbine, Compose UI test, Espresso).

3. **Red — write failing tests first.** Add unit tests for the domain/view-model logic and, where the change is user-visible, a UI/E2E test for the flow. Run `./gradlew testDebugUnitTest` (see `android-test`) and confirm they fail for the right reason.

4. **Green — minimal implementation.** Write the least code that makes the tests pass. Respect:
   - **SOLID** — single responsibility, depend on abstractions, keep classes substitutable.
   - **KISS / YAGNI** — solve only the present requirement; no speculative abstractions or dependencies.
   - **DRY** — factor duplication only once it actually appears.

5. **Refactor — green stays green.** Improve naming and structure with tests passing the whole time. Re-run the suite.

6. **Widen coverage.** Add edge-case and error-path tests; for user flows, validate with the UI/instrumented test.

7. **Report** the acceptance criteria, the tests added, the files changed, and the final test result.

## Constraints
- Tests come before implementation — do not write the production code first and back-fill tests.
- No broad architectural rewrites unless the task explicitly requires them.
- Do not claim done without green test output as evidence.

For committing the result, hand off to `comment-and-commit` or `comment-commit-and-pr`.

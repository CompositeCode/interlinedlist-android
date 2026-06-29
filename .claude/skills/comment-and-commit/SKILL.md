---
name: comment-and-commit
description: Review the current working changes, add clear in-code comments / doc comments where they genuinely aid understanding, then stage and commit with a well-formed message. Use when the user wants to document and commit pending changes in one step.
allowed-tools: Bash, Read, Edit, Grep, Glob
---

# Comment and Commit

Document the pending changes in-code, then commit them. Do not push and do not open a PR — that is `comment-commit-and-pr`.

## Steps

1. **Survey the changes.** Run `git status` and `git diff` (both staged and unstaged) to see exactly what changed. If there are no changes, stop and say so.

2. **Add comments where they add value.** Edit only the changed regions, and only where a comment earns its place:
   - Public/exported declarations get doc comments — **KDoc** (`/** ... */`) for Kotlin, **Javadoc** for Java, matching whatever the surrounding code already uses.
   - Non-obvious logic, workarounds, invariants, and "why" decisions get a brief inline comment.
   - Do **not** restate what the code plainly says, do not comment trivial getters/lambdas, and match the existing comment density and style of each file. Under-comment rather than over-comment.

3. **Re-review.** Run `git diff` again to confirm the comment edits read well and nothing else changed.

4. **Branch if needed.** Check the current branch. If it is the default branch (`main`), create a focused feature branch first (e.g. `feature/<short-topic>`); otherwise commit on the current branch.

5. **Stage and commit.** `git add` the relevant files, then commit with a [Conventional Commits](https://www.conventionalcommits.org/) message (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`) whose subject is imperative and ≤72 chars, with a short body if the change needs context. End the message with:

   ```
   Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
   ```

6. **Report.** Print the resulting commit hash, the branch, and the one-line subject.

## Constraints
- Never amend or rewrite existing commits unless explicitly asked.
- Never `git push` from this skill.
- Keep comment edits surgical — do not reformat or refactor surrounding code.

---
name: comment-commit-and-pr
description: Add in-code comments to the pending changes, commit them, push the branch, and open a GitHub pull request. Use when the user wants to document, commit, and raise a PR for the current work in one step.
allowed-tools: Bash, Read, Edit, Grep, Glob
---

# Comment, Commit, and PR

Document the pending changes, commit them, then push and open a pull request. This is `comment-and-commit` followed by a push and a `gh` PR.

## Steps

1. **Comment and commit.** Perform every step of the `comment-and-commit` workflow: survey `git status` / `git diff`, add doc/inline comments only where they add value (KDoc for Kotlin, Javadoc for Java, matching existing style), branch off `main` if currently on it, then `git add` and commit with a Conventional Commits message ending in:

   ```
   Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
   ```

2. **Confirm the branch.** Never open a PR from `main`. If the commit landed on `main`, move it onto a feature branch before continuing.

3. **Push.** Push the branch and set upstream: `git push -u origin <branch>`.

4. **Open the PR.** Use the GitHub CLI:
   ```
   gh pr create --base main --head <branch> --title "<title>" --body "<body>"
   ```
   - Title: concise summary of the change.
   - Body: a short **Summary** of what changed and why, a **Testing** section (commands run / still needed), and end with:

     ```
     🤖 Generated with [Claude Code](https://claude.com/claude-code)
     ```

5. **Report.** Print the commit hash, the branch, and the PR URL returned by `gh`.

## Constraints
- Opening a PR is outward-facing — make sure the commit and branch are correct before pushing.
- If `gh` is not authenticated or no GitHub remote exists, stop after the commit and tell the user what is missing instead of guessing.
- Do not force-push.

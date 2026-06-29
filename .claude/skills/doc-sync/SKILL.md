---
name: doc-sync
description: Audit the repository's documentation for accuracy and drift against the actual code and against interlinedlist.com, then update or flag stale, missing, or contradictory content. Use after features change or when checking that docs still match reality.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
---

# Doc Sync

Keep the documentation true to the code and aligned with the product site.

## Steps

1. **Inventory the docs.** Find documentation across the repo (`README.md`, `docs/`, in-code doc comments, contributing/setup notes). Use `git log` / `git diff` to see what code changed recently and may have outdated a doc.

2. **Check against ground truth.** For each doc, verify claims against the actual code and configuration:
   - Commands, paths, module names, and setup steps still exist and work.
   - Described features and behaviors match the implementation — flag anything removed, renamed, or changed.
   - Version numbers, dependencies, and screenshots aren't stale.

3. **Check alignment with interlinedlist.com.** Where docs describe the product (not just the implementation), confirm the messaging is consistent with **interlinedlist.com** and link to it as the source of truth rather than duplicating canonical product copy.

4. **Resolve drift.** For clear, low-risk fixes (broken paths, renamed commands, dead links, obvious staleness), update the docs directly. For ambiguous or product-level discrepancies, list them for the user with the file/line and the conflict instead of guessing.

5. **Report** a drift summary: what was corrected, what is flagged for review, and any docs that are missing for newly added features.

## Constraints
- Don't "fix" a doc by changing it to match a bug — if code and doc disagree, surface it; the intended behavior may be the doc's.
- Don't introduce unverified product claims.
- Keep edits surgical and preserve each doc's existing voice and structure.

To create missing documentation surfaced here, use `write-doc`.

---
name: write-doc
description: Draft or refine audience-targeted documentation for the Interlined List Android repo — engineer guides, end-user docs, setup, or architecture — in clear, scannable structure aligned with interlinedlist.com. Use when creating or updating a documentation page.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
---

# Write Documentation

Produce clear, accurate, audience-appropriate documentation for the Interlined List Android project.

## Steps

1. **Name the audience and purpose.** Decide who the doc is for and what they need to do after reading it:
   - **Engineers** — setup, repository structure, architecture, workflows, testing, contribution practices.
   - **End users** — what the app does, how to use it, common scenarios, where to get help; plain language, minimal jargon.
   - **Other personas** — maintainers, product stakeholders, support; adjust depth and tone.

2. **Gather ground truth.** Read the relevant code, README, and existing docs so the content reflects what the project actually does. Never invent features, screens, or capabilities that do not exist.

3. **Draft for scanning.** Use a clear title, short sections with descriptive headings, bullet lists, and runnable examples/commands. Separate product-facing content from implementation detail. Lead with the most important information.

4. **Link the source of truth.** When the doc touches product context or public messaging, link to **interlinedlist.com**. Cross-link related docs in the repo.

5. **Place the file** sensibly (e.g. `docs/` for guides, `README.md` for the entry point) and tell the user where it went.

6. **Report** the target audience, the file path, any links to interlinedlist.com, and suggested reviewers or next steps.

## Constraints
- No unverified claims about the app or its capabilities.
- Don't make end-user docs overly technical unless the audience requires it.
- Keep it specific to Interlined List — no generic boilerplate that could describe any app.

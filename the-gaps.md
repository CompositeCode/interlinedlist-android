# Feature Gaps: Android app vs. interlinedlist.com

_Analysis date: 2026-07-31 • Branch: `feature/strata-branding-and-message-fix`_

> **BUILD STATUS (updated 2026-07-31).** 9 of 14 milestones are built, unit-tested, and
> integrated on branch **`feature/parity-wip`** (local, not pushed) — `:app:assembleDebug` is
> GREEN with all of them wired into the app. **Done:** A Direct Messages (new "DMs" tab), B auth
> lifecycle (register/reset/verify), E notification preferences, F sharing & share links, G
> document collaboration + delta sync, K account & session security, L browse public content, M
> lists+documents polish. **Remaining (all user-gated):** D moderation + I message-edit + H
> cross-posting (touch `:feature:messages` — the uncommitted branding branch, so deferred until
> that lands; H also needs OAuth redirect URIs), C push/FCM (needs a Firebase project), J billing
> (needs a Play-billing-policy decision), N GitHub (optional). Follow-ups: WorkManager runtime
> bootstrap for delta-sync (device-verified), app-manifest deep-link intent-filters, and a 2nd +
> a non-subscriber test account for live E2E.

Compares the native Android client against the online app
([interlinedlist.com](https://interlinedlist.com)) and its REST API. Built from the
**authoritative OpenAPI spec** (`/api/openapi.json` — **189 paths**) diffed against the
Retrofit interfaces actually in the repo, and then **verified live** against the API using
the test account in `.env` (bearer sync-token). Endpoints marked **✓live** were called and
their real response shapes confirmed on 2026-07-31.

> **Live-verification notes**
> - The `.env` base URL has a **trailing slash** (`…com/`) → `//api/...` 308-redirects and
>   drops POST bodies. The app's Retrofit base must be slash-normalized (it is, via Retrofit),
>   but any raw tooling must strip it. Auth confirmed working; token field is `token`.
> - A cluster of endpoints returns `{"error":"Unauthorized"}` **even with a valid bearer
>   token** — they are **cookie/session-gated web-only** surfaces: `/api/user/dashboard-layout`,
>   `/api/user/front-wall-layout`, `/api/user/engagement`, `/api/auth/accounts`. These are the
>   web dashboard / "front wall" / multi-account-switcher and are **out of scope for the native
>   bearer client** (see §4).
> - The OpenAPI WebFetch summary earlier **hallucinated** `/api/messages/feed`,
>   `/api/messages/thread/{id}`, a `/cross-posts` status endpoint, and notification
>   bulk-read/clear-all. **None exist** in the real spec — so the app is already at parity there.

---

## 1. The Gaps (verified parity scorecard)

| # | Area | Real endpoints (verified where noted) | Android | Verdict |
|---|------|---|:---:|---|
| 1 | **Direct Messages** | `GET/POST /api/dm` ✓live, `/dm/unread-count` ✓live `{count}`, `/dm/recipients` ✓live, `/dm/thread/{username}`(+`/updates`), `/dm/{id}/read\|trash\|restore`, `/dm/images/upload` | ❌ | **Missing entirely** |
| 2 | **Account lifecycle** | `POST /api/auth/register`, `/forgot-password`, `/reset-password`, `/verify-email`, `/send-verification-email`, `/verify-email-change` | ❌ | **Missing** (login-only) |
| 3 | **Push notifications** | `POST /api/push/register`, `DELETE /api/push/unregister` | ❌ | Missing (Phase 9) |
| 4 | **Moderation** | `GET /api/user/blocks` ✓live, `/api/user/mutes` ✓live, `POST/DELETE /api/users/{username}/block`, `.../mute`, `POST /api/users/{username}/report` | ⚠️ | Only *report message* today |
| 5 | **Notification preferences** | `GET/PATCH /api/user/notification-preferences` ✓live (per-event push/inApp/email channels) | ❌ | **Missing** |
| 6 | **Sharing / share links** | lists+docs `GET/POST/DELETE …/share-links`, `/{lists,documents}/shared/{token}` (GET/POST); `GET /api/lists/watching` ✓live | ❌ | **Missing** |
| 7 | **Document collaboration** | `…/{id}/collaborators` (GET/POST/PUT/DELETE), `/collaborators/users`, `…/{id}/presence` (POST/DELETE) | ❌ | **Missing** |
| 8 | **Document delta sync** | `GET/POST /api/documents/sync`, `GET /api/documents/tree`, `PATCH /api/documents/{id}` | ❌ | Missing (offline-first core) |
| 9 | **Social linking + cross-post** | `GET /api/user/identities` ✓live, provider `authorize`/`status`, `/api/linkedin/*`, cross-post via targets on `POST /api/messages` | ⚠️ | Read-only status only |
| 10 | **Message edit** | `PATCH /api/messages/{id}` | ❌ | Missing |
| 11 | **Billing (Stripe)** | `POST /api/stripe/create-checkout-session`, `/create-portal-session` | ❌ | Missing (gates, no upsell) |
| 12 | **Account management** | `GET/DELETE /api/user/identities` ✓live, `POST /api/user/identities/verify`, `POST /api/user/change-email/request`, `POST /api/user/delete` | ❌ | **Missing** |
| 13 | **Active sessions / security** | `GET /api/user/sessions` ✓live, `DELETE /api/user/sessions/{id}` | ❌ | **Missing** |
| 14 | **Browse others' public content** | `GET /api/users/{username}/lists` (+`/{id}`,`/{id}/data`), `/documents`, `/messages`, `/api/users/lookup` | ⚠️ | Profile only, not their content |
| 15 | **List metadata update** | `PUT /api/lists/{id}` | ❌ | Missing (create/delete only) |
| 16 | **List folders rename/move/delete** | `PUT/DELETE /api/folders/{id}` | ⚠️ | Read/create only |
| 17 | **List extras** | `GET /api/lists/{id}/contributors`, single row `GET …/data/{rowId}`, `GET /api/lists/shared/{token}/data` | ⚠️ | Partial |
| 18 | **Doc extras** | `POST /api/documents/folders/{id}/documents`, `POST /api/documents/templates/seed-defaults` | ⚠️ | Partial |
| 19 | **GitHub integration** | `GET/POST /api/github/issues`, `PATCH …/{owner}/{repo}/{number}`, comments, repos, assignees, labels | ❌ | Missing |
| 20 | **LinkedIn org page** | `/api/organizations/{id}/linkedin/status\|assignments\|credential\|sync-pages` | ❌ | Missing |
| 21 | **Mutual follows** | `GET /api/follow/{userId}/mutual` | ❌ | Missing (minor) |

Legend: ✅ full · ⚠️ partial · ❌ none · **✓live** = response confirmed against prod today.

### Already at parity (verified — do **not** rebuild)
Login (sync-token ✓live), current user + public profiles + user search, **Following** (all
paths incl. requests ✓live), **Lists** (CRUD-ish, schema, refresh, watchers, connections, data
rows, folder browse/create), **Messages feed** (post/reply/dig/delete/search, image+video
upload with `/api/limits` ✓live limits, scheduled list, report, link-preview metadata),
**Documents** (CRUD, search, image upload, folders, templates, from-template),
**Notifications** (list/read/mark-all/delete — the API has *no* bulk-read or clear-all, so this
is complete), **Organizations** (CRUD + members), **Integrations** (CSV exports, limits,
connected-account status).

### Priority tiers
- **P0 — net-new subsystems users immediately miss:** #1 DMs, #2 account lifecycle, #3 push.
- **P1 — big holes in shipped features:** #4 moderation, #5 notif-prefs, #6 sharing, #7 doc
  collab, #8 doc sync, #9 social linking/cross-post, #10 message edit, #11 billing.
- **P2 — account/settings & completeness:** #12 account mgmt, #13 sessions, #14 public content,
  #15–#18 list/doc polish.
- **P3 — niche/minor:** #19 GitHub, #20 LinkedIn org, #21 mutual.

---

## 2. Implementation Plan

Preserves the established architecture (per-feature self-contained module: own Retrofit API +
own Room DB `interlinedlist-<domain>.db`, MVVM, offline-first, Hilt/KSP, Compose/M3 using
`MaterialTheme.colorScheme` roles — never invented brand-color vals). Every item is **TDD-first**
(`tdd-feature` skill), validated with `android-test` before commit. For parallel waves, use the
proven method: pre-scaffold + push to `dev`, then N `android-feature-engineer` agents in
**worktree isolation** (`git reset --hard origin/dev` at STEP 0), orchestrator wires nav.

> **Pre-flight (whole effort):** the DTO/envelope quirks are real (see `api-response-conventions`
> — `data` wrapping, `author` vs `user`, explicit nulls; keep `coerceInputValues=true`). Capture
> each endpoint's live body before writing DTOs. Verified shapes for the new areas:
> DM inbox `{items:[…], nextCursor}`; blocks `{blockedUsers:[…], pagination}`; notif-prefs
> `{events:[{key,label,description,channels:{push,inApp,email}}]}`; sessions
> `{sessions:[{id,deviceLabel,createdAt,lastUsedAt,isCurrent}]}`; identities `{identities:[{id,
> provider,providerUsername,profileUrl,avatarUrl,connectedAt,lastVerifiedAt}]}`; watching
> `{lists:[{…,role}]}`.

---

### Milestone A — Direct Messages `:feature:directmessages` _(P0, largest single gap)_

Complete private 1:1 messaging, parallel to the social `:feature:messages`.

| Capability | Endpoint | Verified shape |
|---|---|---|
| Inbox (cursor paged) | `GET /api/dm` | `{items:[], nextCursor:null}` ✓ |
| Send DM | `POST /api/dm` | — |
| Single message | `GET /api/dm/{id}` | — |
| Mark read | `POST /api/dm/{id}/read` | — |
| Trash / restore | `POST /api/dm/{id}/trash` · `/restore` | — |
| Recipients (who I can DM) | `GET /api/dm/recipients` | `{recipients:[{id,username,displayName,avatar}]}` ✓ |
| Thread by username | `GET /api/dm/thread/{username}` | — |
| Poll new (near-real-time) | `GET /api/dm/thread/{username}/updates` | — |
| Unread badge | `GET /api/dm/unread-count` | `{count:0}` ✓ |
| Image attachment | `POST /api/dm/images/upload` | (obeys `/api/limits` ✓) |

**Steps:** scaffold module (own `DirectMessagesApi`, Room `interlinedlist-dm.db` with
`DmThreadEntity`/`DmMessageEntity`, repo, ViewModels) → Conversations list (cursor paging via
`nextCursor`, unread counts) → Thread (bubbles, image attach, read receipts) → New-message
recipient picker → poll `/thread/{username}/updates` while thread open (mirror notifications
polling) → nav entry with unread badge from `/dm/unread-count`. Tests: repo (MockWebServer)
send/read/trash/restore/merge; ViewModel (Turbine) unread + optimistic send; Compose thread.
**Done:** send/receive against prod, badge updates, thread survives restart, image round-trips.

### Milestone B — Account lifecycle & auth completeness `:feature:auth` _(P0)_

Today: sync-token login + `LoginScreen` only. Add `register`, `forgot-password`,
`reset-password`, `verify-email` (+`send-verification-email`), `verify-email-change`.
**Steps:** add `AuthApi` endpoints + central error mapping (weak pw, email taken); unauth
nav sub-graph Login⇄Register⇄Forgot→Reset; **deep links** for `reset`/`verify-email` email
URLs; post-register mint sync-token → authed shell + "unverified email" banner w/ resend.
Tests: MockWebServer per flow incl. failures; Compose form validation. **Done:** new user can
register, verify, reset password entirely in-app.

### Milestone C — Push notifications (FCM) _(P0, Phase 9 — blocked on Firebase project)_

`POST /api/push/register` / `DELETE /api/push/unregister`. Add Firebase Messaging;
register on new token, unregister on sign-out; runtime `POST_NOTIFICATIONS` perm; channels
per type wired to `notification-preferences` (Milestone E); tap → deep-link. Keep polling as
fallback. **Blocked:** needs `google-services.json` from owner.

### Milestone D — Moderation `:feature:profile`/`:feature:messages` _(P1)_

| Capability | Endpoint | Verified |
|---|---|---|
| My blocks list | `GET /api/user/blocks` | `{blockedUsers:[], pagination}` ✓ |
| My mutes list | `GET /api/user/mutes` | `{mutedUsers:[], pagination}` ✓ |
| Block / unblock | `POST` / `DELETE /api/users/{username}/block` | — |
| Mute / unmute | `POST` / `DELETE /api/users/{username}/mute` | — |
| Report user | `POST /api/users/{username}/report` | — |

**Steps:** moderation overflow menu on profiles + message authors (block/mute/report user,
extending existing *report message*); "Blocked & muted" management screen in Account hub;
local feed filter of blocked/muted authors as a safety net. Tests: block hides author query;
mute/report round-trips.

### Milestone E — Notification preferences `:feature:notifications` _(P1)_

`GET/PATCH /api/user/notification-preferences` ✓live — per-event (`dig`, `push`, `follow`,
`mention`, `reply`, …) with `channels:{push,inApp,email}`. Build a settings screen (grouped
toggles per event×channel), PATCH on change, optimistic + rollback. Feeds channel routing for
Milestone C. Tests: parse the events model; toggle→PATCH ViewModel test.

### Milestone F — Sharing & share links (lists + documents) _(P1)_

Lists+docs `GET/POST/DELETE …/share-links`; resolve `/{lists,documents}/shared/{token}` (GET
read-only, POST claim edit/admin); `GET /api/lists/shared/{token}/data`; **"Shared with me"**
list backed by `GET /api/lists/watching` ✓live (returns lists with a `role`). Share sheet
(create link w/ role, copy URL, list, revoke); deep-link handler for `…/shared/{token}` →
preview → optional claim. Tests: create/revoke; claim flow; deep-link unit.

### Milestone G — Document collaboration & delta sync `:feature:documents` _(P1)_

Delta sync via WorkManager (`GET/POST /api/documents/sync` + cursor; switch edits `PUT`→`PATCH`
for optimistic concurrency, surface conflicts); collaborators UI (`…/collaborators` CRUD +
`/collaborators/users` search, roles viewer/editor/admin); lightweight presence
(`…/{id}/presence` heartbeat while open, "N here" avatars); use `GET /api/documents/tree` for
sidebar. Full live-cursor CRDT is a stretch goal — start presence + PATCH-concurrency + LWW.
Tests: scripted-delta reconcile; conflict-on-PATCH; collaborator role change. **Done:** edit
offline, reconnect, no clobber; invited collaborator sees the doc.

### Milestone H — Social linking & cross-posting `:feature:integrations`/`:feature:messages` _(P1)_

`GET /api/user/identities` ✓live shows linked providers (mastodon, linkedin, twitter, bluesky).
OAuth connect via **Custom Tabs** (`/authorize` → deep-link back → `/status`); manage/unlink via
`DELETE /api/user/identities` + `/identities/verify`. Compose: destination chips (linked
networks) → cross-post targets on `POST /api/messages`. LinkedIn posting targets
(`/api/linkedin/posting-targets|targets|sync-pages`) + org page
(`/api/organizations/{id}/linkedin/*`). Tests: identity-parse; compose-with-targets. _(Note: no
dedicated `/cross-posts` status endpoint exists — status rides the message create response.)_

### Milestone I — Message edit `:feature:messages` _(P1, small)_

`PATCH /api/messages/{id}` — edit affordance on own messages, optimistic Room update, "edited"
marker. Tests: edit round-trip.

### Milestone J — Billing / subscription `:feature:billing` (new, thin) _(P1, Phase 10)_

`POST /api/stripe/create-checkout-session` + `/create-portal-session`. On the already-mapped
403 "requires active subscription" → upsell screen → checkout URL in Custom Tabs; "Manage" →
portal URL; refresh `GET /api/user` on return for new `customerStatus`. **Confirm Play policy**
(Stripe web checkout for cross-platform SaaS entitlements) before Play submission. Tests:
gating→upsell nav; status refresh.

### Milestone K — Account management & security `:feature:profile` _(P2)_

`GET/DELETE /api/user/identities` ✓live (view/unlink connected accounts),
`POST /api/user/change-email/request`, `POST /api/user/delete` (account deletion — Play
requires an in-app path), and **active sessions**: `GET /api/user/sessions` ✓live
(`deviceLabel`, `lastUsedAt`, `isCurrent`) + `DELETE /api/user/sessions/{id}` (revoke device).
Build a Settings → Security/Account section. Tests: session-list render + revoke; delete-account
confirm flow.

### Milestone L — Browse others' public content _(P2)_

`GET /api/users/{username}/lists` (+`/{id}`, `/{id}/data`), `/documents`, `/messages`,
`/api/users/lookup`, `GET /api/follow/{userId}/mutual`. Extend the profile screen with tabs
(their Lists / Documents / Posts) and show mutual-follow context. Tests: profile-tabs load.

### Milestone M — List & document completeness polish _(P2, parallel-agent fodder)_

- Lists: `PUT /api/lists/{id}` (rename/metadata); folders `PUT/DELETE /api/folders/{id}`
  (rename/move/delete); `GET /api/lists/{id}/contributors`; single row `GET …/data/{rowId}`.
- Documents: `POST /api/documents/folders/{id}/documents` (create-in-folder);
  `POST /api/documents/templates/seed-defaults`.
Each: endpoint + minimal UI + MockWebServer/ViewModel tests.

### Milestone N — GitHub integration `:feature:integrations` _(P3)_

`/api/github/repos`, `issues` (list/create), `PATCH …/{owner}/{repo}/{number}`, comments,
assignees, labels, next-issue-number — for users who manage GitHub-backed lists. Optional.

---

## 3. Suggested delivery sequence

| Wave | Milestones | Rationale |
|---|---|---|
| **1** | A (DMs), B (auth lifecycle), I (msg edit) | Biggest user-visible gaps; unblock onboarding + private messaging; edit is cheap. |
| **2** | D (moderation), E (notif-prefs), F (sharing), M (polish) | High-value, additive to shipped modules, parallelizable. |
| **3** | G (doc collab+sync), H (social linking/cross-post) | Heavier; sync is the offline-first payoff, OAuth needs Custom-Tabs. |
| **4** | K (account/security), L (public content), J (billing) | Settings-surface + monetization; J gated on Play policy sign-off. |
| **5** | C (push/FCM), N (GitHub) | C gated on Firebase project; N optional. |

**Definition of done for parity:** every ✅/⚠️→✅ row verified live against prod, unit + Compose
tests green via `android-test`, each milestone merged to `dev` behind the nav shell.

---

## 4. Explicitly out of scope (verified web-only / N/A for a native bearer client)

- **Web dashboard / "front wall" + widgets** — `/api/user/dashboard-layout`,
  `/api/user/front-wall-layout` (both **✓ returned `Unauthorized` to a valid bearer token** —
  cookie-gated web UI), and the widget data feeds `/api/widgets/{news,markets,bike-share}`,
  `/api/weather`, `/api/location`. A native home-screen widget would be a separate product idea,
  not API parity.
- **Multi-account switcher** — `/api/auth/accounts` **✓ `Unauthorized` to bearer**; `switch`/
  `remove-account`/`{provider}/link` are cookie-session flows. Web-only.
- **`/api/user/engagement`** — **✓ `Unauthorized` to bearer**; web analytics surface.
- **Admin** (`/api/admin/*`), **cron** (`/api/cron/*`), **webhooks** (`/api/webhooks/*`),
  **materialize / architecture-aggregates / analytics-ingest**, `/api/images/proxy`,
  `/api/oauth/client-metadata`, `/api/test-db` — server-side, privileged, or internal.

## 5. Open questions for the owner
1. **Firebase project** for push (Milestone C) — when available?
2. **Play billing policy** — Stripe web checkout acceptable, or Play Billing required? (Milestone J.)
3. **DM placement** — dedicated bottom-nav tab vs. an envelope entry in the top bar?
4. Should the **web dashboard/front-wall** ever get a native equivalent, or stay web-only (§4)?

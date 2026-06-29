# InterlinedList Android — Build-Out Roadmap

The Android client for **InterlinedList** ("Productivity, Connected."). This document
is the durable copy of the approved build-out plan. It targets **full parity** with the
InterlinedList REST API, delivered in sequenced, independently shippable phases.

Confirmed product decisions:
- **Scope:** full API parity, phased.
- **Offline:** offline-first — Room cache reconciled via the API's sync/delta endpoints.
- **Push:** deferred — notifications by polling `GET /api/notifications`; FCM added in Phase 9.
- **Release:** built release-ready (signed AAB + APK); Play vs GitHub channel chosen near launch.

## Architecture

MVVM + clean layering (UI → ViewModel → Repository → {Retrofit API, Room cache}), offline-first
with Room as the single source of truth, unidirectional data flow.

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation-Compose (single activity) |
| DI | Hilt (KSP) |
| Async | Coroutines + Flow |
| Networking | Retrofit + OkHttp + kotlinx.serialization; `Authorization: Bearer` interceptor |
| Persistence | Room (+ Paging 3) |
| Secure storage | EncryptedSharedPreferences for the `il_tok_` token |
| Background sync | WorkManager |
| Images | Coil |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| Testing | JUnit, Turbine, MockWebServer, Compose UI test, Espresso |

### Module layout
```
:app                  entry, DI graph, navigation host, theme
:core:model           pure-Kotlin domain models
:core:common          result/error types, dispatchers, session token contract
:core:designsystem    brand theme (colours, Play typeface), logo composables
:core:network         Retrofit, OkHttp, auth interceptor, DTOs
:core:database        Room database, DAOs, entities
:core:datastore       encrypted session store
:feature:*            added per phase (auth, lists, messages, documents, …)
```

### Brand
Colours: Ocean Blue `#0F4C5F`, Emerald `#34A56D`, Amber Gold `#F9AF36`, Near Black `#1A1A1A`.
Typeface: **Play** (OFL). Logo assets live in `brand/`; the adaptive launcher icon uses the
mark on an Ocean Blue background. Assets are used as-is — never recoloured or distorted.

## API integration
- Base URL `https://interlinedlist.com/`. Bearer auth via `POST /api/auth/sync-token` → `il_tok_…`.
- Pagination: `{data, pagination:{total,limit,offset,hasMore}}` → Paging 3 `RemoteMediator`.
- Errors: central mapper for `{error}` + status codes; 403 "requires an active subscription"
  becomes an upsell state gated on `customerStatus`.
- Sync: `GET/POST /api/documents/sync` (+ list/message refresh) via WorkManager; Room reconciliation.

## Phases

| # | Phase | API surface |
|---|---|---|
| 0 | **Scaffold & brand foundation** ✅ in progress | Gradle/modules, design system, network/db/datastore foundations, CI |
| 1 | Authentication & session | `/api/auth/*` (sync-token, login, register, forgot/reset, verify-email) |
| 2 | User & profiles | `/api/users/me`, `/api/users/{username}`, profile edit, avatar |
| 3 | Lists (namesake) | `/api/lists` CRUD, `/schema`, `/data`, watchers, connections, search, folders |
| 4 | Messages (social feed) | `/api/messages` feed, replies, dig, scheduled, media upload, search |
| 5 | Following & notifications | `/api/follow/*`; `/api/notifications` (polling) |
| 6 | Documents | `/api/documents` CRUD, markdown editor, `/documents/sync`, templates, folders |
| 7 | Organizations | org management, members |
| 8 | Integrations & exports | cross-posting, GitHub/LinkedIn, CSV exports, utility endpoints |
| 9 | Push notifications (FCM) | `/api/push/register` — requires Firebase project + `google-services.json` |
| 10 | Hardening & release | upsell flows, a11y, R8, signing, store assets, internal track |

Each phase is TDD-first and verified via the `android-test` skill before commit.

## Prerequisites

**To build locally:** JDK 17, Android SDK (platform `android-35`, build-tools `35.0.0`),
`local.properties` with `sdk.dir`. See `README.md`.

**To release (gathered ahead of launch):** upload keystore + `keystore.properties` (gitignored),
app ID `com.interlinedlist.android`, Play Console account (if Play), store-listing assets derived
from `brand/`, privacy policy + data-safety form, and a Firebase project for push (Phase 9).

See the full annotated plan in the approved planning document for prerequisite tables and rationale.

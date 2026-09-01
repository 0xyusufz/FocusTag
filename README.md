# FocusTag

**A Kotlin/Jetpack Compose Android app that turns a physical or simulated NFC tap into a verified, tamper-resistant "Focus Session" — automatically blocking distracting apps until the tag is tapped again.**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Backend](https://img.shields.io/badge/Backend-Supabase-3ECF8E?logo=supabase&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-informational)
![Status](https://img.shields.io/badge/Status-Active%20Development%20(Phase%205%2F10)-yellow)

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Required Permissions](#required-permissions)
- [Roadmap](#roadmap)
- [Known Limitations](#known-limitations)
- [Testing](#testing)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

FocusTag is a **digital-wellbeing / self-focus Android application** that replaces willpower with a physical ritual: tap an NFC tag (or a simulated tag during development) to start a **Focus Session**. While a session is active, FocusTag enforces a per-app blocking policy in real time using Android's `AccessibilityService`, so distracting apps are locked out until the same tag is tapped again to end the session.

The project is built around a **policy-driven, strategy-based enforcement engine** rather than a hardcoded blocklist — every installed app is classified (`CORE`, `RESTRICTED`, `ALLOWABLE`, `SYSTEM_REQUIRED`, `UNCLASSIFIED`) and resolved against user overrides to produce a `BLOCK` / `ALLOW` / `PROTECTED` decision, all wrapped in a concurrency-safe, self-healing enforcement coordinator with a full audit ledger.

Authentication, user profiles, and session history are backed by **Supabase** (Postgres + Auth), with background sync handled by **WorkManager**.

## Key Features

| Status | Feature |
|:---:|---|
| ✅ | **Supabase Authentication** — email/password sign-up & login with deep-link session handling |
| ✅ | **User Profile Management** — profile creation and sync against a Postgres backend |
| ✅ | **App Inventory & Policy Engine** — scans installed apps and classifies them into enforcement categories, with user-configurable overrides |
| ✅ | **NFC-Driven Focus State Engine** — toggles Focus Mode on tag scan (real or simulated tag support for development/testing) |
| ✅ | **Real-Time App Blocking** — `AccessibilityService`-based enforcement strategy that blocks restricted apps the instant they're opened |
| ✅ | **Escape-Protection Detection** — flags attempts to bypass an active focus session |
| ✅ | **Self-Healing Enforcement** — health monitoring (`HEALTHY` / `DEGRADED` / `FAILED` / `INACTIVE` / `RECOVERING`) with bounded automatic recovery |
| ✅ | **Auditable Enforcement Ledger** — every block/unblock action is recorded with mechanism, timestamp, and session ID |
| ✅ | **Orphaned-Session Recovery** — detects and safely closes sessions left in an inconsistent state (e.g., after a crash) |
| ✅ | **Background History Sync** — `WorkManager`-scheduled sync of session history to Supabase |
| 🚧 | **Session Dashboard** — historical analytics and streak tracking (planned) |
| 🚧 | **Production NFC Tag Registry** — durable NTAG-based tag provisioning (planned) |
| 🚧 | **Device Owner / Managed Mode** — stronger, uninstall-resistant enforcement via the Device Admin API (scaffolded, not yet activated) |

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI Toolkit | Jetpack Compose, Material 3 |
| Architecture | MVVM + layered (data / domain / ui / util), Strategy & Coordinator design patterns |
| Concurrency | Kotlin Coroutines, `StateFlow`, `Mutex`-guarded state transitions |
| Backend-as-a-Service | Supabase (Auth, Postgrest / Postgres) |
| Networking | Ktor Client (Android engine) |
| Serialization | kotlinx.serialization |
| Background Work | Android WorkManager |
| Device Integration | AccessibilityService API, NFC API, Device Admin API |
| Build System | Gradle (Kotlin DSL), Android Gradle Plugin 9 |
| Testing | JUnit4, Espresso, Compose UI Test |

## Architecture

FocusTag follows a **layered MVVM architecture** with a clear separation between UI, domain logic, and data access:

```
UI (Jetpack Compose)  →  Domain (enforcement + state engines)  →  Data (repositories, Supabase, services)
```

The heart of the app is the **enforcement subsystem**, designed as a swappable-strategy state machine:

```
                         ┌─────────────────────┐
   NFC tap / API call →  │ FocusStateEngine     │  toggles FOCUS_ACTIVE / NORMAL
                         └──────────┬───────────┘
                                    ▼
                         ┌─────────────────────┐
                         │ PolicyEngine         │  resolves BLOCK / ALLOW / PROTECTED
                         │ (per-app category +  │  per installed app
                         │  user overrides)     │
                         └──────────┬───────────┘
                                    ▼
                         ┌─────────────────────┐
                         │ EnforcementCoordinator│  mutex-guarded start / stop / reconcile
                         │ (session lifecycle,   │  orphan-session cleanup, status publishing
                         │  ownership guard)     │
                         └──────────┬───────────┘
                                    ▼
                         ┌─────────────────────┐
                         │ EnforcementStrategy   │  pluggable enforcement backend
                         │ (interface)           │
                         └──┬────────────────┬───┘
                            ▼                ▼
                 AccessibilityEnforcement   NoOpEnforcement
                 Strategy (production)      Strategy (fallback/testing)
```

**Design highlights (useful context for code review):**
- **Strategy pattern** decouples *how* an app is blocked (Accessibility Service today, Device Owner planned) from the coordination logic that decides *when* to block.
- **Mutex-guarded session lifecycle** (`startEnforcement`, `stopEnforcement`, `reconcile`) prevents race conditions when NFC events, lifecycle callbacks, and background sync fire concurrently.
- **Immutable enforcement snapshots + ledger** give every session a reconstructable audit trail of exactly which packages were blocked, by which mechanism, and when.
- **Ownership guarding** prevents two sessions (e.g., after a process restart) from fighting over device-level enforcement.
- **Orphan detection** (`checkAndHandleOrphans`) reconciles state if the app is killed mid-session, marking the session `INTERRUPTED` instead of leaving stale locks in place.

## Project Structure

```
app/src/main/java/com/focustag/app/
├── data/
│   ├── model/          # Data classes & enums (Enforcement, Focus, AppInventory, Profile, SessionHistory)
│   ├── repository/     # AppInventory, AppPolicy, Auth, Enforcement, Focus, Profile, SessionHistory repos
│   ├── service/        # FocusTagAccessibilityService (real-time app-block enforcement)
│   ├── receiver/       # FocusDeviceAdminReceiver (Device Admin API, planned Managed Mode)
│   ├── supabase/       # SupabaseModule (Auth + Postgrest client provider)
│   └── worker/         # SyncWorker / SyncScheduler (WorkManager background sync)
├── domain/
│   ├── EnforcementCoordinator(Hub).kt   # Session lifecycle & concurrency-safe orchestration
│   ├── EnforcementStrategy.kt           # Strategy interface
│   ├── AccessibilityEnforcementStrategy.kt
│   ├── NoOpEnforcementStrategy.kt
│   ├── FocusStateEngine.kt              # NFC tag → Focus state transitions
│   └── PolicyEngine.kt                  # Per-app BLOCK/ALLOW/PROTECTED resolution
├── ui/
│   ├── auth/            # Login, Signup, Home screens + AuthViewModel
│   ├── apps/            # App selection screen + ViewModel
│   ├── focus/           # FocusViewModel
│   ├── profile/         # Profile screen + ViewModel
│   └── theme/           # Material 3 theming
├── util/                # Accessibility/NFC capability checkers, NfcController
└── MainActivity.kt
```

## Getting Started

### Prerequisites
- Android Studio (latest stable)
- JDK 11+
- An Android device or emulator running **API 26+**
- A [Supabase](https://supabase.com) project (free tier is sufficient) for Auth + Postgrest

### Setup

1. **Clone the repository**
```bash
   git clone <repository-url>
   cd FocusTag
```

2. **Configure Supabase credentials**
   Create a `local.properties` file in the project root (this file is git-ignored and never committed) and add:
```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_PUBLISHABLE_KEY=your-publishable-key
```
   These are injected into the app at build time via `BuildConfig`.

3. **Build and run**
```bash
   ./gradlew assembleDebug
```
   or open the project in Android Studio and run the `app` configuration on a device/emulator.

## Required Permissions

| Permission / Component | Purpose |
|---|---|
| `INTERNET` | Supabase Auth & Postgrest communication |
| `NFC` | Reading physical focus tags |
| Accessibility Service | Detects foreground app changes to enforce blocking in real time (user must enable manually in system settings) |
| Device Admin (`BIND_DEVICE_ADMIN`) | Reserved for the planned Managed Mode / Device Owner enforcement tier — currently registers no active policies |

## Roadmap

FocusTag is being built in ten planned phases; the project currently sits at the **end of Phase 5**:

1. ✅ Authentication
2. ✅ Student/User Profile
3. ✅ App Inventory + Policy Engine
4. ✅ Focus State Engine + Simulated NFC
5. ✅ Consumer App Enforcement (Accessibility-based blocking, health monitoring, audit ledger)
6. ⬜ Real NFC + Tag Registry
7. ⬜ Focus Session Tracking
8. ⬜ Dashboard
9. ⬜ Production NFC/NTAG Integration
10. ⬜ Security Hardening → Managed Mode / Device Owner enforcement

## Known Limitations

Being transparent about current constraints:

- **Visibility Control** (hiding blocked apps from the launcher) is **not supported** on standard (non–Device Owner) installs.
- **Network-level restriction** via `VpnService` is **not supported** due to platform limitations on non-Device-Owner installs; app-level blocking via Accessibility Service is the current enforcement mechanism.
- Automated instrumentation/unit test coverage is minimal at this stage (default Android Studio templates only) — expanding coverage of `EnforcementCoordinator` and `PolicyEngine` is a near-term priority.
- Real (physical) NFC tag provisioning and a persistent tag registry are not yet implemented; the current build supports simulated tag events for development.

## Testing

```bash
./gradlew test               # JVM unit tests
./gradlew connectedAndroidTest  # Instrumented tests (requires a connected device/emulator)
```

## Contributing

Contributions, issues, and feature suggestions are welcome.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes with clear, descriptive messages
4. Open a pull request describing the change and its motivation

## License

No license file is currently included in this repository. Until one is added, all rights are reserved by the author. (For an open-source portfolio project, adding an [MIT License](https://choosealicense.com/licenses/mit/) is recommended.)

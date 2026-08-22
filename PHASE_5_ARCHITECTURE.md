# Phase 5 Architecture

## Final flow

```text
FocusState
    -> PolicyEngine / ResolvedPolicy
    -> EnforcementPolicy
    -> EnforcementCoordinator
       |-> App Blocking
       |   |-> AccessibilityService
       |   |-> ForegroundAppDetector
       |   |-> AppBlockingModule / AppBlockingDecision
       |   `-> BlockingActivity
       |-> Visibility Control
       |   `-> UNSUPPORTED on ordinary Android
       |-> Network Restriction
       |   `-> UNSUPPORTED without packet processing
       `-> Escape Protection
           `-> partial capability and detection

EnforcementCoordinator + module observations
    -> HealthMonitor
    -> RecoveryManager
    -> existing App Blocking monitoring restart
    -> AuditEvent through the shared EnforcementLogger
```

## Responsibilities and sources of truth

- `PolicyEngine` resolves installed-app policy into `ResolvedPolicy`.
- `EnforcementPolicy` is the single enforcement representation.
- `EnforcementStateMachine` owns lifecycle transitions.
- `EnforcementCoordinator` starts/stops modules and retains their latest capability statuses.
- `AppBlockingModule` is the only runtime owner of `AppBlockingDecision` evaluation.
- `FocusTagAccessibilityService` receives Android window-state events.
- `AccessibilityForegroundAppDetector` forwards foreground snapshots to App Blocking and Escape Protection observers.
- `DefaultHealthMonitor` observes fresh lifecycle, policy, App Blocking, Accessibility, and optional-capability state.
- `DefaultRecoveryManager` performs bounded, verified App Blocking monitoring restart only.
- `AuditEvent` records meaningful lifecycle, decision, health, capability, escape, and recovery events through `EnforcementLogger`.

No component in the audit layer changes policy or performs enforcement.

## Capability status

```text
App Blocking
    SUPPORTED / core

Visibility Control
    UNSUPPORTED under normal Android privileges

Network Restriction
    UNSUPPORTED because per-app VPN selectors alone cannot deny blocked traffic

Escape Protection
    PARTIAL: Accessibility/Usage capability and Settings/service conditions are detectable;
    Android does not allow silent permission repair or device lockdown
```

Only a failed core App Blocking capability is globally fatal. Optional `UNSUPPORTED` capabilities remain metadata and do not stop core enforcement. Optional degraded capabilities produce `DEGRADED` health while App Blocking remains operational.

## Lifecycle

Normal startup is `STARTING -> ACTIVE` only after module starts return without fatal failure. Health becomes `HEALTHY` only when Focus Mode is active, policy exists, the coordinator is active, App Blocking is started and monitoring, and AccessibilityService is available.

Focus stop invokes module cleanup, clears the blocking experience, transitions to `INACTIVE`, and resets the health snapshot. Process recreation starts health and recovery state fresh; persisted FocusState and policy reconstruction must be supplied again before a healthy state can be reported.

Core failure produces `FAILED`. Recovery may restart existing App Blocking monitoring only when AccessibilityService and policy are available. Recovery is capped at three attempts with a 30-second cooldown and performs no delayed retry loop. Accessibility disabled and missing policy produce `USER_ACTION_REQUIRED`. Unsupported optional modules never cause recovery loops.

## Audit events

`AuditEvent` is platform-independent and contains only structured diagnostics: event type, timestamp, severity, source, optional lifecycle/health state, optional capability, and an optional non-sensitive reason. It does not store traffic, URLs, passwords, tokens, browsing history, or packet contents.

Identical App Blocking package/decision pairs are audited only on transition, preventing repeated foreground callbacks from flooding the event stream. Health events are emitted only when the aggregate health state changes. No audit database is introduced; `InMemoryAuditEventStore` is sufficient for the current phase.

## Android limitations

AccessibilityService requires explicit user enablement and remains user-controlled. Visibility Control cannot hide arbitrary launcher icons. Network Restriction does not establish a VPN and does not claim network denial. Escape Protection detects some conditions but cannot prevent Settings access, force-stop, uninstall, or device configuration changes. These limitations are reported as unsupported, degraded, failed, or user-action-required states rather than hidden or false success.

Phase 6 capabilities such as audit persistence, stronger device management, kiosk behavior, network packet processing, and additional enforcement mechanisms are not part of this implementation.

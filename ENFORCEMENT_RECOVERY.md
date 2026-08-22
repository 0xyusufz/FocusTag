# Enforcement Recovery Manager

## Purpose

Recovery coordinates bounded restart attempts for existing enforcement components. It does not implement app blocking, resolve policies, launch `BlockingActivity`, enable AccessibilityService, modify Settings or packages, start VPNs, or perform visibility control.

```text
HealthSnapshot
    -> RecoveryManager
    -> bounded recovery action
    -> verify result
    -> recovered, failed, exhausted, or user action required
```

Recovery does not grant FocusTag additional Android privileges.

## Recoverable failures

The current safe recovery action is restarting existing App Blocking foreground monitoring when:

- Focus health is `FAILED`;
- AccessibilityService is currently available;
- a valid active policy is supplied;
- the existing App Blocking component can register a new monitoring handle.

The restart stops the stale monitoring handle, reuses the existing policy and detector, registers the existing callbacks, checks that a handle was returned, and evaluates the current foreground snapshot. It does not create a second policy or enforcement pipeline.

## Non-recoverable failures

- AccessibilityService disabled or unavailable: `USER_ACTION_REQUIRED`. The user must enable it in Android Settings. FocusTag cannot enable it silently and does not repeatedly launch Settings.
- Missing policy: `USER_ACTION_REQUIRED`. Recovery does not invent a default or block-everything policy.
- Unsupported Visibility Control, Network Restriction, or other optional capability: no recovery attempt.
- Inactive, healthy, degraded, or already recovering health: no recovery attempt.

## Retry limits and cooldown

Transient App Blocking recovery is limited to three attempts. A 30-second cooldown follows each attempt. After the limit is reached, the manager returns `EXHAUSTED` with `USER_ACTION_REQUIRED`. A confirmed recovery or explicit lifecycle reset clears the attempt counter.

The manager performs no delayed background retry loop. Callers own when a new recovery request is made, which keeps the recovery cycle bounded and battery-conscious.

## Recovery events

Structured recovery events are emitted through the existing `EnforcementLogger`:

- `RECOVERY_REQUESTED`
- `RECOVERY_STARTED`
- `RECOVERY_ACTION`
- `RECOVERY_VERIFICATION`
- `RECOVERY_SUCCEEDED`
- `RECOVERY_FAILED`
- `RECOVERY_EXHAUSTED`
- `USER_ACTION_REQUIRED`

## Verification

A restart is successful only when the App Blocking recovery operation returns `STARTED`, meaning the detector accepted a new monitoring registration. A return from the recovery method without a started status is failure. The Health Monitor remains the authoritative source for subsequent healthy/recovering state checks; RecoveryManager does not mark policy or Accessibility requirements healthy by assumption.

## Process recreation and lifecycle

A new manager starts with no retry history. The application must reconstruct FocusState, EnforcementPolicy, coordinator state, App Blocking state, and Accessibility availability, then perform a fresh health check. If the core is failed but recoverable, a bounded restart may be requested. If Accessibility is unavailable, the result remains user action required.

Focus stop should stop enforcement and discard the manager instance or call `reset()`. No delayed recovery callback is scheduled by this manager, so stopping Focus cannot trigger a later restart. Optional unsupported modules remain unsupported and never create recovery loops.

## Known limitations

Recovery cannot guarantee that Android will keep AccessibilityService alive, prevent force-stop or uninstall, control Settings, or restore enforcement while the process is dead. It does not implement a RecoveryManager for policy reconstruction because the current architecture does not expose a safe policy rebuild callback; missing policy therefore requires user/application lifecycle action rather than a fabricated policy.

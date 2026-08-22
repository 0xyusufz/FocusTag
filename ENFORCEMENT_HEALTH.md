# Enforcement Health Monitor

## Purpose

The Health Monitor observes whether the active enforcement policy is currently enforceable. It does not block applications, launch `BlockingActivity`, change policies, start VPNs, enable AccessibilityService, or modify Android Settings.

## Health states

- `INACTIVE`: Focus Mode is not active, or monitoring has been stopped.
- `HEALTHY`: Focus Mode is active, a policy exists, the coordinator is active, App Blocking is started and monitoring, and AccessibilityService is available. Optional unsupported capabilities alone do not make core health unhealthy.
- `DEGRADED`: Core App Blocking remains operational, but an optional capability is degraded or failed. Optional `UNSUPPORTED` status is retained as capability metadata and is non-fatal.
- `FAILED`: Core App Blocking cannot currently enforce the policy. Examples include missing policy, failed App Blocking startup, inactive monitoring, unavailable AccessibilityService, or a coordinator recovery-required state.
- `RECOVERING`: A monitor that previously observed `FAILED` sees the core signals restored. This is a transitional observation only; no RecoveryManager or automatic retry is implemented. The next healthy check can report `HEALTHY`.

## Capability aggregation

```text
CORE
`-- App Blocking

OPTIONAL
|-- Visibility Control
|-- Network Restriction
`-- Escape Protection
```

Core failures determine `FAILED`. Optional failures and `DEGRADED` states determine `DEGRADED` only when App Blocking remains operational. Optional `UNSUPPORTED` states do not stop App Blocking and do not alone create `FAILED` health.

Example:

```text
App Blocking: STARTED
Visibility: UNSUPPORTED
Network: UNSUPPORTED
Escape Protection: DEGRADED

Overall: DEGRADED
```

## Health snapshot

`EnforcementHealthSnapshot` records:

- overall health state;
- core and optional capability statuses;
- Focus State and coordinator lifecycle state;
- App Blocking status;
- Accessibility availability;
- policy availability;
- timestamp;
- a non-personal failure reason.

No package names, traffic data, browsing history, or personal information are stored by the monitor.

## Lifecycle and process recreation

The coordinator retains the latest module status per capability for observation. A newly constructed `DefaultHealthMonitor` starts at `INACTIVE`; it must be given a fresh observation from current Focus State, policy, coordinator state, App Blocking monitoring state, and Accessibility availability before reporting health. It does not trust stale in-memory state.

Focus start initializes monitoring and checks the complete observation. Focus stop resets the snapshot to `INACTIVE`. Accessibility loss makes health `FAILED` because the core foreground interception path cannot reliably operate. Accessibility reconnection may produce `RECOVERING`; it does not silently re-enable the service or retry enforcement.

The monitor is event-compatible: callers can invoke `checkHealth` when focus, coordinator, module, policy, or AccessibilityService events occur. It performs no aggressive polling and adds no Android background work.

## Android limitations

AccessibilityService availability is user-controlled. The monitor can report unavailable state but cannot restore it. Visibility Control and Network Restriction are currently unsupported or partial capabilities as documented by their respective modules. Escape Protection reports detectable degradation but cannot prevent Settings access, force-stop, uninstall, or device-level changes under normal Android privileges.

# Escape Protection

## Phase 5.5 scope

Escape detection is not escape prevention. A normal Android application is not a device administrator.

FocusTag reports capability health and detectable escape routes through the existing enforcement logger. It does not attempt hidden package-management behavior, system-UI manipulation, or repeated Settings launches.

## Detectable routes

| Escape route | Detection | Mitigation in this phase |
| --- | --- | --- |
| AccessibilityService disabled | Checked when focus starts and polled while active; service interruption and destruction are observed | Report `DETECTED` / `DEGRADED`; the user must re-enable the service in Android Settings |
| Usage Access revoked | Checked when focus starts and polled while active through `AppOpsManager` | Report `DETECTED` / `DEGRADED`; Usage Access remains supporting observation only |
| Settings opened | Accessibility foreground events identify `com.android.settings` | Emit a structured event; do not block or manipulate Settings |
| AccessibilityService interruption | AccessibilityService lifecycle callback | Emit `ENFORCEMENT_SERVICE_UNAVAILABLE`; do not claim continuous enforcement |
| Process interruption | Observable only after a service or application restart | Reinitialize from persisted focus state when the app is recreated |
| Target application missing or disabled | Can be discovered during later inventory/policy refresh | Report or reconcile stale policy later; no package mutation is attempted |

## Unsupported routes

The following cannot be prevented by an ordinary installation:

- Re-enabling AccessibilityService after the user disables it.
- Preventing the user from opening or changing Android Settings.
- Preventing FocusTag from being force-stopped.
- Preventing FocusTag from being uninstalled.
- Preventing another application from being disabled or removed.
- Force-stopping arbitrary applications.
- Maintaining enforcement while the process is stopped and Android has not restarted it.

The controller represents these limitations as `UNSUPPORTED` where they are assessed. It never claims that the device is locked down.

## Lifecycle

- Focus start checks AccessibilityService and Usage Access, registers one observer on the existing foreground event bus, and begins capability polling.
- Focus active reports capability changes once per state transition and reports Settings foreground events through `EnforcementLogger`.
- Focus stop removes listeners, cancels polling, and clears transient state.
- Process recreation creates a new controller and repeats capability checks from current Android state; it does not trust in-memory flags.
- Escape Protection is a partial capability. `DEGRADED` and `UNSUPPORTED` results do not stop App Blocking.

There is no automatic Settings redirect, no emergency-functionality block, and no loop that repeatedly opens FocusTag or Android Settings. The user can safely stop Focus Mode through the existing FocusTag flow.

## Stronger deployment

Preventing uninstall, force-stop, accessibility changes, Settings access, or application disablement would require Device Owner / `DevicePolicyManager`, Android Enterprise managed-device enrollment, kiosk/lock-task deployment, or privileged OEM control. Those mechanisms are explicitly outside Phase 5.5.

No new permissions or manifest services were added. The existing AccessibilityService requires explicit user enablement, and `PACKAGE_USAGE_STATS` remains an app-op granted by the user in Settings.

# Visibility / Launch Surface Control

## Phase 5.4 result

FocusTag reports visibility control as `UNSUPPORTED` on ordinary Android installations. It does not hide, disable, uninstall, suspend, or mutate another installed application's launcher activity or package state.

The existing App Blocking capability remains responsible for blocked applications:

```text
EnforcementPolicy
    +--> App Blocking -> AccessibilityService -> BlockingActivity
    `--> Visibility Control -> UNSUPPORTED on ordinary Android
```

## Android mechanism

The project uses no launcher replacement, overlay permission, device-admin declaration, Device Owner provisioning, or kiosk configuration for this phase. `PackageManager` launcher discovery is used by the app inventory, but package visibility does not grant authority to modify the discovered applications.

A normal third-party application can:

- discover launcher activities permitted by package visibility rules;
- open another application's exported launcher activity;
- observe foreground windows when the user enables FocusTag's AccessibilityService;
- place FocusTag's own activity in front of a blocked application on a best-effort basis.

A normal third-party application cannot reliably:

- remove another application's launcher icon;
- disable another application's launcher activity;
- change another application's package metadata;
- suspend or force-stop another application.

Removing or disabling another application's launch surface requires a managed-device or privileged deployment, such as Device Owner / `DevicePolicyManager`, OEM privileges, or control of the default launcher. Those mechanisms are intentionally excluded from Phase 5.4. Becoming the default launcher would only allow FocusTag to control its own launcher surface; it would not grant ordinary package-management authority over arbitrary applications.

## Permissions and configuration

No new permission is required or added. The existing `PACKAGE_USAGE_STATS` permission remains observational, and the existing AccessibilityService remains the Phase 5.3 foreground interception mechanism. The AccessibilityService must still be enabled by the user for app blocking; it does not grant launcher-icon mutation authority.

The app targets SDK 37 with a minimum SDK of 26. Stock Android and OEM launchers may differ in how they expose launcher activities, but none of those differences grant a normal app authority to hide arbitrary installed applications.

## Lifecycle behavior

- Focus start invokes the visibility controller once through `EnforcementCoordinator`; it returns `UNSUPPORTED`.
- `UNSUPPORTED` is logged and does not prevent App Blocking from becoming active.
- No repeated visibility operations occur while focus remains active because no platform operation is attempted.
- Focus stop returns `STOPPED`; there is no changed visibility state to restore.
- Process recreation safely reconstructs the same stateless unsupported result from the current policy. No in-memory visibility state is assumed.
- Policy changes do not trigger launch-surface mutations. Live policy refresh remains deferred with the existing enforcement architecture.

This phase does not change the installed-app inventory, policy selections, package metadata, application data, or installed applications.

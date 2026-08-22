# Network Restriction

## Phase 5.6 result

Network Restriction is intentionally reported as `UNSUPPORTED` for the current implementation. The existing `EnforcementPolicy` is consumed, but FocusTag does not establish a VPN or claim that blocked applications have lost network access.

## Why VpnService is not enabled here

Android `VpnService` supports per-app routing selectors:

- `addAllowedApplication(packageName)`: listed applications use the VPN; other applications bypass it.
- `addDisallowedApplication(packageName)`: listed applications bypass the VPN; other applications use it.

These selectors do not mean that an application is allowed or denied Internet access. In particular, putting blocked packages in the disallowed list would make them bypass the VPN, not block their traffic. Putting non-blocked packages in the allowed list would still leave blocked packages on ordinary networking.

True selective denial would require routing the relevant applications into the VPN and processing the TUN interface so traffic can be dropped. Packet processing, traffic inspection, HTTPS interception, URL logging, and traffic storage are explicitly outside Phase 5.6. Therefore a VPN service would not provide the requested behavior without implementing a misleading or incomplete restriction.

## Architecture boundary

```text
EnforcementPolicy
    +--> App Blocking -> AccessibilityService -> BlockingActivity
    +--> Visibility Control -> unsupported on ordinary Android
    +--> Escape Protection -> partial detection
    `--> Network Restriction -> unsupported without packet processing
```

`AndroidNetworkRestrictionController` implements the existing `NetworkRestrictionEnforcer` boundary. It reuses `AppBlockingDecisionEvaluator` to respect `PROTECTED > BLOCKED > ALLOWED > UNKNOWN`, reports the capability limitation, and does not mutate network state.

## Permissions and VPN consent

No VPN service or VPN permission was added because no VPN is established in this implementation. A future real VPN implementation would require:

- a manifest service protected by `android.permission.BIND_VPN_SERVICE`;
- the `android.net.VpnService` service intent;
- explicit user consent from `VpnService.prepare()`;
- handling for consent denial, revocation, competing VPNs, `establish()` failure, `onRevoke()`, and service destruction;
- foreground-service lifecycle handling on supported Android versions.

FocusTag cannot bypass VPN consent, silently take over another VPN, or claim network enforcement before `VpnService.Builder.establish()` succeeds.

## Policy and protected applications

No package names are hardcoded. The controller receives `EnforcementPolicy` and uses the existing decision evaluator. Protected applications are never treated as blocked, including when a package appears in both blocked and protected sets. Allowed and unknown packages retain the existing policy behavior. App Blocking continues independently when Network Restriction is unsupported.

## Lifecycle and failure behavior

- Focus start invokes the controller once through `EnforcementCoordinator` and receives `UNSUPPORTED`.
- Focus remains active without repeated VPN creation or consent prompts.
- Focus stop reports that no VPN state requires restoration.
- Process recreation does not assume a VPN object exists; the stateless controller reports the same unsupported capability when recreated.
- Policy or package errors do not cause package mutation or traffic logging.
- App Blocking remains functional because the coordinator treats `UNSUPPORTED` as non-fatal.

## Capability declaration

- Per-app restriction: **NO in the current implementation**.
- Protected-app bypass: **NOT APPLICABLE; no VPN is established**.
- Network enforcement after process death: **NO; no VPN is established**.
- Offline blocking: **NOT APPLICABLE**.

A future implementation could provide actual restrictions with a carefully designed TUN packet-forwarding/drop engine, but that would be a separate security-sensitive scope requiring explicit privacy, routing, DNS, IPv4/IPv6, consent, service-restart, and device testing work. Device Owner or managed-device privileges could provide stronger network policy options, but those are also outside this phase.

No user traffic contents, URLs, packet payloads, browsing history, or HTTPS data are inspected, stored, or uploaded.

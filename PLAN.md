# Plan: Bluetooth Device Pairing

## Goal

Add an **Add Device** action to the main screen. Tapping it:

1. Requests the Bluetooth permissions needed for discovery on the current Android version.
2. Starts a classic Bluetooth discovery scan.
3. Shows nearby devices in a TV-friendly dialog.
4. Starts pairing (`BluetoothDevice.createBond()`) when a device is selected.
5. Shows pairing progress/failure and refreshes the paired-device list when bonding completes.

## Current state

- `MainActivity` only lists already-bonded devices from `BluetoothAdapter.getBondedDevices()`.
- There is no scan, pairing UI, `BLUETOOTH_SCAN`, or `ACCESS_FINE_LOCATION` permission.
- `minSdk 26`, `targetSdk/compileSdk 34`.
- UI is a two-pane `ConstraintLayout`; the right pane contains the *Paired Devices* header and list.
- Device rows are `MaterialCardView`s with D-pad focus support.

## UX flow

1. User focuses **Add Device** in the *Paired Devices* header and presses it.
2. Missing runtime permissions are requested (see matrix below). A denial shows an explanatory toast.
3. On Android 10–11, Location services are checked first; if off, a dialog offers **Open Settings**.
4. The scan dialog opens immediately. Discovery starts, with an indeterminate progress bar and live result count.
5. Results appear as focusable cards: device name, MAC address, and a `PAIRED` badge for already-bonded devices.
6. Selecting an unpaired device:
   - Discovery is cancelled.
   - `createBond()` is called; the row shows `PAIRING…`.
   - The system pairing/PIN dialog handles confirmation.
   - `BOND_BONDED` shows a success toast, dismisses the scan dialog, and refreshes the main list.
   - `BOND_NONE` after `BOND_BONDING` shows a failure state; the user can retry or rescan.
7. Selecting an already-paired device shows "already paired" and closes the dialog.
8. If discovery ends with no results, an empty state explains how to put the device in pairing mode.
9. **Scan Again** restarts discovery; **Cancel**/Back cancels discovery and unregisters the receiver.

## Permission matrix

| Android version | Runtime permissions | Additional requirement |
|---|---|---|
| 12+ (API 31+) | `BLUETOOTH_SCAN` (with `neverForLocation`), `BLUETOOTH_CONNECT` | None |
| 8–11 (API 26–30) | `ACCESS_FINE_LOCATION` | Location services enabled on Android 10–11 |

Manifest additions:

```xml
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-feature android:name="android.hardware.location" android:required="false" />
```

`BLUETOOTH_CONNECT` is already declared and is needed for names, bond state, and `createBond()`. The optional location feature declaration prevents the implied required feature from blocking installation on TVs without location hardware.

## Implementation

### 1. Layouts and resources

- `res/layout/activity_main.xml`
  - Convert the header into a horizontal row.
  - Add `add_device_button` (Material button) at the trailing edge.
  - Re-anchor the device list below the header row.
- `res/layout/dialog_scan_devices.xml` (new)
  - Card dialog: title, status text, indeterminate progress bar, `RecyclerView` (240dp), empty state, **Scan Again** and **Cancel** buttons.
- `res/layout/item_scan_device.xml` (new)
  - Focusable `MaterialCardView` with Bluetooth icon, name, address, and bond badge.
  - Focused stroke changes to `colorPrimary`.
- `res/color/scan_item_stroke.xml` (new) for the focus/normal stroke selector.
- `res/values/strings.xml`
  - Add all Add Device, scan status, pairing, permission, and location strings.
  - No hardcoded user-facing text in Java.

### 2. `DeviceScanDialog` (new)

`Dialog` subclass that owns discovery and pairing for the lifetime of the dialog:

- Registers a context receiver for:
  - `BluetoothDevice.ACTION_FOUND`
  - `BluetoothAdapter.ACTION_DISCOVERY_FINISHED`
  - `BluetoothDevice.ACTION_BOND_STATE_CHANGED`
  - `BluetoothAdapter.ACTION_STATE_CHANGED`
- Uses `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)` for protected system broadcasts.
- Starts discovery only when the adapter is enabled and permissions are present.
- Deduplicates results by MAC address.
- Cancels discovery on dismissal and unregisters the receiver exactly once.
- Calls `createBond()` for unbonded selections; tracks the pending device by address.
- Reports success through `OnPairingCompleteListener` so `MainActivity` can refresh.
- Handles the Bluetooth-off and scan-start-failure states in-dialog.

### 3. `ScanDeviceAdapter` (new)

- Holds discovered devices in discovery order while scanning.
- Deduplicates results without re-binding unchanged rows.
- Sorts unbonded devices first, then by name, when discovery finishes.
- Handles missing `BLUETOOTH_CONNECT` by falling back to "Unknown Device".
- Shows `PAIRED` / `PAIRING…` badges and ignores clicks while a bond is in flight.

### 4. `MainActivity`

- Find and wire `add_device_button`.
- `requestPairingPermissionsAndScan()`:
  - Checks the API-specific permission set, requests only missing permissions.
  - Re-checks in `onRequestPermissionsResult`; opens the dialog only when all are granted.
- Checks Location services before opening the dialog on Android 11 and below.
- Shows the scan dialog, passing a callback that runs `updateDeviceList()` and `updateStatus()`.
- Dismisses the dialog in `onDestroy()` to avoid leaking the receiver/activity.

### 5. Versioning and docs

- Bump `versionCode` to `3`, `versionName` to `1.3.0`.
- Update the version pill in the main layout to `v1.3`.
- Add the pairing feature to `README.md` and document the scan permissions.

## Signing and build

- The release keystore lives outside the repository at `../cjw.keystore`.
- Add a Gradle `signingConfigs.release` that reads `keystore.properties` from the repository root.
- `keystore.properties` is **gitignored** and contains:

```properties
storeFile=../cjw.keystore
storePassword=<from cjw.keystore.txt>
keyAlias=cjw
keyPassword=<from cjw.keystore.txt>
```

- Build with `assembleRelease`; output is `app/build/outputs/apk/release/AndroidTVBluetooth.apk`.
- Verify with `apksigner verify --print-certs` and `aapt dump badging`.

## Edge cases

- Bluetooth off: dialog shows "Bluetooth is off"; **Scan Again** retries after the user enables it.
- Permission denied: toast; the Android 12+ Nearby Devices permission can be granted via system settings or ADB.
- Location off on API 29–30: blocking dialog with a shortcut to Location settings.
- Device name unavailable without `BLUETOOTH_CONNECT`: show "Unknown Device" instead of crashing.
- Bonding cancelled or failed: keep the result list, show failure status, allow retry/rescan.
- Discovery finishes with zero results: show pairing-mode hint.
- Dialog dismissed during discovery/bonding: cancel discovery, unregister receiver, drop callbacks.
- Activity destroyed while dialog is open: dialog is dismissed before the activity receiver is unregistered.
- Duplicate scan results: deduplicated by address.

## Verification

1. `:app:assembleRelease` completes without errors.
2. APK is signed with the `cjw` certificate (compare SHA-256 fingerprint).
3. `aapt dump badging` shows version 1.3.0, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `ACCESS_FINE_LOCATION` (maxSdk 30).
4. Static review confirms every discovery/bond receiver registration has a matching unregister on dismissal.
5. On-device testing is left to the user (per request), including:
   - Grant permissions on Android 12+ and Android 11.
   - Scan, select an unbonded device, confirm the system pairing prompt.
   - Verify success/failure toasts and main-list refresh.
   - Cancel a scan and reopen the dialog.

## Files touched

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Scan/location permissions |
| `app/src/main/java/com/saihgupr/btcontrol/MainActivity.java` | Add Device wiring, permission flow, dialog lifecycle |
| `app/src/main/java/com/saihgupr/btcontrol/DeviceScanDialog.java` | New discovery/pairing dialog |
| `app/src/main/java/com/saihgupr/btcontrol/ScanDeviceAdapter.java` | New scan result adapter |
| `app/src/main/res/layout/activity_main.xml` | Add Device button |
| `app/src/main/res/layout/dialog_scan_devices.xml` | New dialog layout |
| `app/src/main/res/layout/item_scan_device.xml` | New result row |
| `app/src/main/res/color/scan_item_stroke.xml` | Focus stroke selector |
| `app/src/main/res/values/strings.xml` | New strings |
| `app/build.gradle` | Signing config and version bump |
| `.gitignore` | Ignore `keystore.properties` |
| `README.md` | Feature/usage/permission docs |
| `PLAN.md` | This plan |

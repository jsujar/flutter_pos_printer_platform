# flutter_pos_printer_platform — USB-only fork

> **This is a fork.** It is not a drop-in replacement for the upstream package:
> **Bluetooth and BLE support is gone from the public API.** Anything that imports
> `PrinterType.bluetooth`, `BluetoothPrinterInput` or the Bluetooth connectors will
> not compile against it.

Maintained at [jsujar/flutter_pos_printer_platform](https://github.com/jsujar/flutter_pos_printer_platform),
forked from [diantahoc/flutter_pos_printer_platform](https://github.com/diantahoc/flutter_pos_printer_platform).

## What this fork changes

### 1. Fixes the USB permission crash on Android 12+ and 13+

Connecting a USB printer crashed the host app on Android 14. One bug with two faces,
both of them platform requirements the upstream plugin predates:

- The `PendingIntent` used to request USB permission declared neither mutability nor a
  target package. Since Android 12 it must declare `FLAG_MUTABLE` or `FLAG_IMMUTABLE`,
  and since Android 14 an implicit intent inside a mutable one must name its package.

  `FLAG_IMMUTABLE` is **not** the fix: the permission dialog has to write `EXTRA_DEVICE`
  and `EXTRA_PERMISSION_GRANTED` into the reply intent, and an immutable `PendingIntent`
  never receives them — the receiver would read `getBooleanExtra(EXTRA_PERMISSION_GRANTED,
  false)` and permission would look denied every single time. The fix is to keep
  `FLAG_MUTABLE` and make the intent explicit with `setPackage()`.

- `registerReceiver` did not declare export behaviour, mandatory since Android 13.

`compileSdkVersion` moves from 31 to 34 because the code now references
`Build.VERSION_CODES.TIRAMISU` and `Context.RECEIVER_NOT_EXPORTED`. `targetSdkVersion` is
deliberately left alone: in a library it does not govern runtime behaviour — the host
app's `targetSdk` does.

### 2. Removes Bluetooth and BLE support

This is the breaking change, and it was a deliberate trade, not a cleanup. Three reasons,
in order of weight:

**The permissions.** The upstream manifest declared seven permissions that **every host
app inherited without asking for them**:

```
BLUETOOTH · BLUETOOTH_ADMIN · BLUETOOTH_SCAN · BLUETOOTH_ADVERTISE
BLUETOOTH_CONNECT · ACCESS_FINE_LOCATION · ACCESS_COARSE_LOCATION
```

Two of those are location permissions. Google Play makes you justify them in the store
listing, and users see them at install time. A point-of-sale tablet asking for precise
location because of a *printer* plugin is indefensible, and no amount of explaining makes
it look better.

**A whole class of crash disappears with it.** The `bluetoothService` field was `lateinit`
and assigned in `onAttachedToActivity()` *after* `adapter.init()`. When that init threw —
and on Android 14 it always did, see reason 1 — the field stayed unassigned and the app
crashed on teardown with `UninitializedPropertyAccessException`. The visible symptom was a
crash on closing the app, which is about as far from the real cause as a stack trace can
get. Remove the field and the failure mode cannot happen.

**Dead weight.** The app this fork serves prints over USB and nothing else: there was not a
single call into the Bluetooth side, while the code kept a BLE scanner and a bonded-device
receiver alive in the background.

**Scope, stated precisely:** the Dart API and the Android native side have no Bluetooth
left. The **iOS native sources still contain the BLE classes** (`BLEConnecter` and friends)
— they are unreachable, because nothing in Dart calls into them any more, but they are
still compiled in. Removing them was out of scope: this fork was driven by an Android bug
and the iOS path is untested here. Say so rather than claim a clean sweep.

If Bluetooth is ever needed again, recover it from git history rather than rewriting it.

### 3. Adds printer status reading over USB

New `readStatus()` / `readPrinterStatus`, which asks the printer for its state with the
ESC/POS real-time command `DLE EOT n` over the bulk IN endpoint — the endpoint upstream
ignored, picking up only the OUT one.

| n | Query |
|---|---|
| 1 | Printer status |
| 2 | Offline status — **includes cover open** |
| 3 | Error status |
| 4 | Paper sensor |

Returns four integers, `-1` wherever the printer did not answer.

⚠️ **Four `-1` values are not a bug in this code.** Plenty of cheap printers expose the IN
endpoint to satisfy the USB spec and then never implement the command. Verified on an
`H58 Printer USB` (vendor `1110`, product `2056`): writes succeed, the IN endpoint is
there, and it answers nothing to all four queries. Check your own hardware before building
anything on top of this.

The reason it exists: a kitchen ticket that never comes out is an order nobody prepares,
and without status reading there is no way to tell whether the printer ran out of paper or
its cover is open. Worth knowing before you rely on it: with the cover open, a bulk write
still **succeeds** — the data sits in the printer's buffer and prints when the cover is
closed again. The return code tells you nothing about the printer's physical state.

## Installing

```yaml
dependency_overrides:
  flutter_pos_printer_platform_image_3:
    git:
      url: https://github.com/jsujar/flutter_pos_printer_platform.git
      ref: fix/android14-usb-permission-and-lifecycle
```

Pin a commit rather than the branch once you depend on it in earnest, so a build today and
a build next month give the same thing.

--------------------------

## Usage

A library to discover printers and send them ESC/POS commands, over **USB and
network (ethernet/wifi)**.

Generate the bytes with
[flutter_esc_pos_utils](https://pub.dev/packages/flutter_esc_pos_utils) and hand them to
this package.

> ⚠️ **`/example` is upstream's and has not been updated for this fork.** It is built
> around Bluetooth — `PrinterType.bluetooth`, `stateBluetooth`, a `BluetoothPrinter` model
> — so it **will not compile** here. The snippets below are the current API; use those.

### What is supported

Only Android was touched and verified in this fork. The other columns are upstream's
claims, left as they were.

|                     | Android | iOS | Windows | Description |
| :------------------ | :-----: | :-: | :-----: | :---------- |
| USB interface       | ✅ | ⬜ | ✅ | Connect to USB devices. |
| Net (ethernet/wifi) | ✅ | ✅ | ✅ | Connect to network devices. |
| `discovery`         | ✅ | ✅ | ✅ | List USB devices, or scan the network. |
| `connect`           | ✅ | ✅ | ✅ | Open a connection to the device. |
| `disconnect`        | ✅ | ✅ | ✅ | Close an active or pending connection. |
| `send`              | ✅ | ✅ | ✅ | Send raw `List<int>` bytes. |
| `stateUSB`          | ✅ | ⬜ | ⬜ | Stream of USB connection state changes. |
| `readStatus`        | ✅ | ⬜ | ⬜ | Ask the printer for its state — **added by this fork**. |

**Removed in this fork:** the Bluetooth classic and BLE interfaces, `PrinterType.bluetooth`,
`BluetoothPrinterInput`, the `isBle` and `autoConnect` parameters and the `stateBluetooth`
stream. See "What this fork changes" above for why.

### Minimum Android SDK

Version 21, as upstream. In `android/app/build.gradle`:

```
    defaultConfig {
        ...
        minSdkVersion 21
        ...
```

### Generate the bytes

```dart
import 'package:esc_pos_utils/esc_pos_utils.dart';

final profile = await CapabilityProfile.load();
final generator = Generator(PaperSize.mm58, profile);
List<int> bytes = [];

bytes += generator.text('Test Print', styles: const PosStyles(align: PosAlign.center));
bytes += generator.text('Product 1');
```

### Discover, connect, print

`PrinterType` has two values: `usb` and `network`.

```dart
import 'package:flutter_pos_printer_platform_image_3/flutter_pos_printer_platform_image_3.dart';

final printerManager = PrinterManager.instance;

// Discover
final devices = <PrinterDevice>[];
printerManager.discovery(type: PrinterType.usb).listen(devices.add);

// Connect
await printerManager.connect(
  type: PrinterType.usb,
  model: UsbPrinterInput(
    name: selected.name,
    productId: selected.productId,
    vendorId: selected.vendorId,
  ),
);

// Or over the network
await printerManager.connect(
  type: PrinterType.network,
  model: TcpPrinterInput(ipAddress: '192.168.1.50'),
);

// Print
await printerManager.send(type: PrinterType.usb, bytes: bytes);

// Disconnect
await printerManager.disconnect(type: PrinterType.usb);
```

### Watch the USB connection state

```dart
PrinterManager.instance.stateUSB.listen((status) {
  // USBStatus.connected, .none, ...
});
```

### Read the printer status (this fork only)

```dart
final status = await UsbPrinterConnector.instance.readStatus();
// [printer, offline (cover open), error, paper sensor]
// -1 means the printer did not answer that query.
```

⚠️ Read the caveats in "Adds printer status reading over USB" above before relying on it.
A printer that answers nothing is not a bug in this code, and a successful write tells you
nothing about whether paper actually came out.

## Credits
- https://github.com/andrey-ushakov/esc_pos_utils
- https://github.com/bailabs/esc-pos-printer-flutter
- https://github.com/feedmepos/flutter_printer/tree/master/packages/flutter_pos_printer
- https://pub.dev/packages/flutter_pos_printer_platform


## Support Original Author

If you think that this project has helped you with your developments, you can support this project, any support is much appreciated.

[![Paypal](https://raw.githubusercontent.com/arthas1888/flutter_pos_printer_platform/main/btn-sm-paypal-payment.png)](https://www.paypal.com/donate/?hosted_button_id=92HK6VNCK7MUY)
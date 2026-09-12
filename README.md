# flutter_pos_printer_platform — USB-only fork

> **This is a fork.** It is not a drop-in replacement for the upstream package:
> **Bluetooth and BLE support has been removed entirely.** Anything that imports
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

## Upstream documentation

A library to discover printers, and send printer commands.

This library allows to print esc commands to printers in different platforms such as android, ios, windows and different interfaces as USB and Wifi/Ethernet.

> ⚠️ Upstream text below may still mention Bluetooth. **This fork does not support it** — see "What this fork changes" above.

Inspired by [flutter_pos_printer](https://github.com/feedmepos/flutter_printer/tree/master/packages/flutter_pos_printer).


## Main Features
* Android, iOS and Windows support
* Scan for bluetooth devices
* Send raw `List<int> bytes` data to a device, review this library to generate ESC/POS commands [flutter_esc_pos_utils](https://pub.dev/packages/flutter_esc_pos_utils).

## Features

|                         |      Android       |         iOS          |      Windows       |            Description            |
| :---------------        | :----------------: | :------------------: | :----------------: | :-------------------------------- |
| USB interface           | :white_check_mark: |  :white_square_button: | :white_check_mark: | Allows connection with usb devices. |
| Bluetooth classic interface | :white_check_mark: |  :white_square_button:  | :white_square_button: | Allows connection with classic bt devices. |
| Bluetooth low energy (BLE) interface | :white_check_mark: |  :white_check_mark:  | :white_square_button: | Allows connection with bt BLE devices. |
| Net (ethernet/wifi) interface | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | Allows connection with network devices. |
| scan                    | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | Starts a scan for only Bluetooth devices or network devices(Android/iOS). |
| connect                 | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | Establishes a connection to the device. |
| disconnect              | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | Cancels an active or pending connection to the device. |
| state                   | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | Stream of state changes for the Bluetooth Device. |
| print                   | :white_check_mark: |  :white_check_mark:  | :white_check_mark: | print bytes. |

## Getting Started

For a full example please check /example folder. Here are only the most important parts of the code to illustrate how to use the library.

Generate bytes to print through [flutter_esc_pos_utils](https://pub.dev/packages/flutter_esc_pos_utils).

```dart
    import 'package:esc_pos_utils/esc_pos_utils.dart';

    final profile = await CapabilityProfile.load();
    final generator = Generator(PaperSize.mm58, profile);
    List<int> bytes = [];

    bytes += generator.text('Test Print', styles: const PosStyles(align: PosAlign.center));
    bytes += generator.text('Product 1');
    bytes += generator.text('Product 2');
```

## Android
Allow to connect bluetooth (classic and BLE), USB and network devices

### Change the minSdkVersion for Android

flutter_pos_printer_platform is compatible only from version 21 of Android SDK so you should change this in android/app/build.gradle:

In build.gradle set
```
    defaultConfig {
        ...
        minSdkVersion 21
        targetSdkVersion 31
        ...
```

select type of device `PrinterType` ( bluetooth, usb, network)

if select bluetooth you can send optional params

- isBle -> allow to connect with bluetooth that supports this technology
- autoconnect -> allow to reconnect when state of device is None

## iOS
Allow to connect bluetooth (BLE) and network devices

## Windows
Allow to connect USB and network devices
To network devices is necessary to set ipAddress


## How to use it
### init a PrinterManager instance

```dart
import 'package:flutter_pos_printer_platform/flutter_pos_printer_platform.dart';

    var printerManager = PrinterManager.instance;

 ```

### scan

```dart
    var devices = [];
    _scan(PrinterType type, {bool isBle = false}) {
        // Find printers
        PrinterManager.instance.discovery(type: type, isBle: isBle).listen((device) {
            devices.add(device);
        });
    }
```

### connect

```dart
_connectDevice(PrinterDevice selectedPrinter, PrinterType type, {bool reconnect = false, bool isBle = false, String? ipAddress = null}) async {
    switch (type) {
      // only windows and android
      case PrinterType.usb:
        await PrinterManager.instance.connect(
            type: type,
            model: UsbPrinterInput(name: selectedPrinter.name, productId: selectedPrinter.productId, vendorId: selectedPrinter.vendorId));
        break;
      // only iOS and android
      case PrinterType.bluetooth:
        await PrinterManager.instance.connect(
            type: type,
            model: BluetoothPrinterInput(
                name: selectedPrinter.name,
                address: selectedPrinter.address!,
                isBle: isBle,
                autoConnect: reconnect));
        break;
      case PrinterType.network:
        await PrinterManager.instance.connect(type: type, model: TcpPrinterInput(ipAddress: ipAddress ?? selectedPrinter.address!));
        break;
      default:
    }
  }
```
### disconnect

```dart
    _disconnectDevice(PrinterType type) async {
        await PrinterManager.instance.disconnect(type: type);
        }
```

### listen bluetooth state
```dart
    PrinterManager.instance.stateBluetooth.listen((status) {
      log(' ----------------- status bt $status ------------------ ');
    });
```

### send bytes to print
```dart
    _sendBytesToPrint(List<int> bytes, PrinterType type) async { 
      PrinterManager.instance.send(type: type, bytes: bytes);
    }

```

## Troubleshooting

error:'State restoration of CBCentralManager is only allowed for applications that have specified the "bluetooth-central" background mode'
info.plist add:

```
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Allow App use bluetooth?</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>Allow App use bluetooth?</string>
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>
```


## Credits
- https://github.com/andrey-ushakov/esc_pos_utils
- https://github.com/bailabs/esc-pos-printer-flutter
- https://github.com/feedmepos/flutter_printer/tree/master/packages/flutter_pos_printer
- https://pub.dev/packages/flutter_pos_printer_platform


## Support Original Author

If you think that this project has helped you with your developments, you can support this project, any support is much appreciated.

[![Paypal](https://raw.githubusercontent.com/arthas1888/flutter_pos_printer_platform/main/btn-sm-paypal-payment.png)](https://www.paypal.com/donate/?hosted_button_id=92HK6VNCK7MUY)
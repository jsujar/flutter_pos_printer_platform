# Legacy example — Bluetooth, does NOT work

`main_bluetooth.dart.txt` is the **upstream example as it was before this fork**. It is
kept here as a reference for anyone who needs to see how the Bluetooth API used to be
wired up, or who wants to recover it.

**It does not compile against this fork, and it never will.** It is built around APIs that
no longer exist:

| It uses | State in this fork |
| :------ | :----------------- |
| `PrinterType.bluetooth` | Removed — the enum is `{ usb, network }` |
| `BluetoothPrinterInput` | Removed |
| `PrinterManager.stateBluetooth` | Removed — use `stateUSB` |
| `discovery(isBle: ...)` | The parameter is gone |

The file extension is `.txt` on purpose: inside `lib/` it would break `flutter analyze`
and `flutter build` for the whole example, and a broken example is worse than no example.

**Use [`../lib/main.dart`](../lib/main.dart) instead.** It is the working example for this
fork: USB and network only, and it also demonstrates `readStatus()`.

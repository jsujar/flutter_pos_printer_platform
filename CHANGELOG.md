## Unreleased — USB-only fork (jsujar)

Not published to pub.dev. Consume it from git; see the README.

* **Fixed** the USB permission crash on Android 12+ and 13+: the `PendingIntent` now
  declares `FLAG_MUTABLE` and an explicit package, and the receiver declares
  `RECEIVER_NOT_EXPORTED`. `compileSdkVersion` raised from 31 to 34.
* **Added** `readStatus()` / `readPrinterStatus`, which queries the printer with the
  ESC/POS real-time command `DLE EOT 1..4` over the bulk IN endpoint.
* **BREAKING:** removed Bluetooth and BLE from the public API and from the Android native
  side — `PrinterType.bluetooth`, `BluetoothPrinterInput`, the Bluetooth connectors, the
  `isBle` / `autoConnect` parameters and the `stateBluetooth` stream. Also drops the seven
  permissions the plugin declared, two of them location permissions. The iOS native BLE
  sources are still present but unreachable.
* **Rewrote `/example`** around USB and network. The upstream Bluetooth example was
  deleted rather than kept as dead weight: it could never compile against this fork, and
  it stays in git history for anyone who needs it. Its Android scaffolding was also
  repaired -- Gradle 7.4 cannot run under the JDK 21 that current Flutter ships, and the
  imperative Gradle plugin loader is no longer supported.

## 1.2.4

* Relax rxdart version to allow library usage in FlutterFlow app builder

## 1.2.3

* Add namespace in build.gradle to be compatible with Gradle 8 (credit: https://github.com/tgarm)
* Fix some errors in Android library (credit: https://github.com/tgarm)
* Update rxdart minor version (credit: https://github.com/ivankasalo)
* Fix windows build (credit: https://github.com/sedess)

## 1.2.2

* Fix incorrect ActivityAware lifecycle hooks
* Fix issue in PendingIntent for Android 14+

## 1.2.1

* Attempt to fix initialized error

## 1.1.0

* Toast msgs english locale default

## 1.0.12

* Resolve minor bug [Android] 12

## 1.0.11

* Resolve minor bug dependecies

## 1.0.10

* Now android supports targetSdkVersion 31

## 1.0.9

* Resolve minor bug [Android] connection

## 1.0.8

* Resolve minor bug [Android] connection

## 1.0.6

* Resolve minor bug [Android] connection

## 1.0.6

* Resolve minor bug windows printer

## 1.0.5

* Get current status bt

## 1.0.4

* Solved Bug windows: USB

## 1.0.3

* Bug android notify events: Bluetooth 

## 1.0.2

* Bug android connection interface: USB 

## 1.0.1

* How to use it.

## 1.0.0

* Initial release.

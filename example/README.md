# Example — USB-only fork

A single screen exercising everything this fork supports: discover printers over USB or
the network, connect, print a sample receipt, and ask the printer for its status.

```bash
cd example
flutter run
```

There is no Bluetooth here, and that is deliberate — see the
[repository README](../README.md).

## What it shows

- `discovery(type: PrinterType.usb)` and the network equivalent by IP address.
- `connect` / `disconnect` / `send` with `UsbPrinterInput` and `TcpPrinterInput`.
- `stateUSB`, because a USB connection drops on its own — cable pulled, printer powered
  off — and a one-off check would not notice.
- `readStatus()`, added by this fork. The log explains what came back, including the
  common case of a printer that answers nothing at all.

⚠️ Two things the example says out loud because they surprised us:

- A successful `send` means **the bytes were accepted, not that paper came out**. With the
  cover open or no paper loaded, the write still succeeds and the job waits in the
  printer's buffer.
- Four `-1` from `readStatus()` is **not a bug**: many cheap printers expose the IN
  endpoint and never implement `DLE EOT`.

## Toolchain

The Android scaffolding was migrated off the imperative `apply from:` Gradle plugin
loader, which modern Flutter refuses to run, and the wrapper moved from Gradle 7.4 to 8.3
— 7.4 cannot run under the JDK 21 that Flutter ships with. AGP 8.2.1 and Kotlin 1.8.22,
plus the `namespace` that AGP 8 requires.

## The old example

Upstream's pre-fork example was built on the Bluetooth API this fork removed, so it could
never compile here and has been deleted. If you ever need to see how that API was wired
up, it is in git history.

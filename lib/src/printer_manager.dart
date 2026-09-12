import 'dart:io';

import 'package:flutter_pos_printer_platform_image_3/flutter_pos_printer_platform_image_3.dart';

/// USB-only fork (2026-09-11): `bluetooth` has been dropped from this enum.
/// Bluetooth support was removed from the plugin entirely -- see the header of
/// FlutterPosPrinterPlatformPlugin.kt.
enum PrinterType { usb, network }

class PrinterManager {
  final tcpPrinterConnector = TcpPrinterConnector.instance;
  final usbPrinterConnector = UsbPrinterConnector.instance;

  PrinterManager._();

  static PrinterManager _instance = PrinterManager._();

  static PrinterManager get instance => _instance;

  Stream<PrinterDevice> discovery({required PrinterType type, TcpPrinterInput? model}) {
    if (type == PrinterType.usb && (Platform.isAndroid || Platform.isWindows)) {
      return usbPrinterConnector.discovery();
    } else {
      return tcpPrinterConnector.discovery(model: model);
    }
  }

  Future<bool> connect({required PrinterType type, required BasePrinterInput model}) async {
    if (type == PrinterType.usb && (Platform.isAndroid || Platform.isWindows)) {
      try {
        var conn = await usbPrinterConnector.connect(model as UsbPrinterInput);
        return conn;
      } catch (e) {
        throw Exception('model must be type of UsbPrinterInput');
      }
    } else {
      try {
        var conn = await tcpPrinterConnector.connect(model as TcpPrinterInput);
        return conn;
      } catch (e) {
        throw Exception('model must be type of TcpPrinterInput');
      }
    }
  }

  Future<bool> disconnect({required PrinterType type, int? delayMs}) async {
    if (type == PrinterType.usb && (Platform.isAndroid || Platform.isWindows)) {
      return await usbPrinterConnector.disconnect(delayMs: delayMs);
    } else {
      return await tcpPrinterConnector.disconnect();
    }
  }

  Future<bool> send({required PrinterType type, required List<int> bytes}) async {
    if (type == PrinterType.usb && (Platform.isAndroid || Platform.isWindows)) {
      return await usbPrinterConnector.send(bytes);
    } else {
      return await tcpPrinterConnector.send(bytes);
    }
  }

  Stream<USBStatus> get stateUSB => usbPrinterConnector.currentStatus.cast<USBStatus>();

  USBStatus get currentStatusUSB => usbPrinterConnector.status;
}

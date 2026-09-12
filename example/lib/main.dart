// Example for the USB-only fork of flutter_pos_printer_platform.
//
// Shows the whole surface of the fork in one screen: discover devices over USB
// or the network, connect, print, and ask the printer how it is doing.
//
// There is no Bluetooth here, and that is the point -- see the repository
// README. The pre-fork Bluetooth example is archived under
// `example/legacy_bluetooth/` and does not compile any more.

import 'dart:async';

import 'package:esc_pos_utils/esc_pos_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_pos_printer_platform_image_3/flutter_pos_printer_platform_image_3.dart';

void main() => runApp(const ExampleApp());

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'POS printer (USB-only fork)',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: const PrinterDemoPage(),
    );
  }
}

class PrinterDemoPage extends StatefulWidget {
  const PrinterDemoPage({super.key});

  @override
  State<PrinterDemoPage> createState() => _PrinterDemoPageState();
}

class _PrinterDemoPageState extends State<PrinterDemoPage> {
  final printerManager = PrinterManager.instance;

  PrinterType _type = PrinterType.usb;
  final List<PrinterDevice> _devices = [];
  PrinterDevice? _selected;

  /// Only used when [_type] is [PrinterType.network].
  final _ipController = TextEditingController(text: '192.168.1.50');

  StreamSubscription<PrinterDevice>? _discovery;
  StreamSubscription<USBStatus>? _usbState;

  USBStatus _status = USBStatus.none;
  String _log = '';

  @override
  void initState() {
    super.initState();

    // The USB connection can drop on its own -- cable pulled, printer powered
    // off, device reset -- so the state is a stream, not a one-off check.
    _usbState = printerManager.stateUSB.listen((status) {
      setState(() => _status = status);
      _say('USB state: $status');
    });
  }

  @override
  void dispose() {
    _discovery?.cancel();
    _usbState?.cancel();
    _ipController.dispose();
    super.dispose();
  }

  void _say(String line) {
    setState(() => _log = '$line\n$_log');
  }

  Future<void> _scan() async {
    setState(() => _devices.clear());
    await _discovery?.cancel();

    _discovery = printerManager.discovery(type: _type).listen(
      (device) => setState(() => _devices.add(device)),
      onError: (Object e) => _say('Discovery failed: $e'),
    );
  }

  Future<void> _connect() async {
    final device = _selected;

    try {
      if (_type == PrinterType.network) {
        await printerManager.connect(
          type: PrinterType.network,
          model: TcpPrinterInput(ipAddress: _ipController.text.trim()),
        );
        _say('Connected to ${_ipController.text.trim()}');
        return;
      }

      if (device == null) {
        _say('Pick a USB device first.');
        return;
      }

      await printerManager.connect(
        type: PrinterType.usb,
        model: UsbPrinterInput(
          name: device.name,
          productId: device.productId,
          vendorId: device.vendorId,
        ),
      );
      _say('Connected to ${device.name}');
    } catch (e) {
      _say('Connect failed: $e');
    }
  }

  Future<void> _disconnect() async {
    await printerManager.disconnect(type: _type);
    _say('Disconnected');
  }

  Future<void> _printSample() async {
    final profile = await CapabilityProfile.load();
    final generator = Generator(PaperSize.mm58, profile);

    var bytes = <int>[];
    bytes += generator.text(
      'Test print',
      styles: const PosStyles(align: PosAlign.center, bold: true),
    );
    bytes += generator.text('USB-only fork');
    bytes += generator.text(DateTime.now().toIso8601String());
    bytes += generator.feed(2);
    bytes += generator.cut();

    try {
      await printerManager.send(type: _type, bytes: bytes);
      // ⚠️ Reaching this line means the bytes were accepted, NOT that paper
      // came out. With the cover open or no paper loaded, the write still
      // succeeds and the job waits in the printer's buffer.
      _say('Bytes sent (this does not prove anything was printed)');
    } catch (e) {
      _say('Send failed: $e');
    }
  }

  /// Added by this fork. See the repository README before relying on it.
  Future<void> _readStatus() async {
    if (_type != PrinterType.usb) {
      _say('Status reading is USB only.');
      return;
    }

    final status = await UsbPrinterConnector.instance.readStatus();
    const labels = ['printer', 'offline (cover)', 'error', 'paper'];

    final readable = [
      for (var i = 0; i < status.length; i++)
        '${labels[i]}: ${status[i] < 0 ? 'no answer' : '0x${status[i].toRadixString(16).padLeft(2, '0')}'}'
    ].join(' · ');

    _say(readable);

    if (status.every((value) => value < 0)) {
      _say('This printer does not answer DLE EOT. Not a bug in the plugin -- '
          'plenty of cheap printers never implement it.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final isNetwork = _type == PrinterType.network;

    return Scaffold(
      appBar: AppBar(
        title: const Text('POS printer — USB-only fork'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(28),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text('USB status: ${_status.name}'),
          ),
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SegmentedButton<PrinterType>(
              segments: const [
                ButtonSegment(value: PrinterType.usb, label: Text('USB')),
                ButtonSegment(
                    value: PrinterType.network, label: Text('Network')),
              ],
              selected: {_type},
              onSelectionChanged: (selection) {
                setState(() {
                  _type = selection.first;
                  _devices.clear();
                  _selected = null;
                });
              },
            ),
            const SizedBox(height: 12),
            if (isNetwork)
              TextField(
                controller: _ipController,
                decoration: const InputDecoration(
                  labelText: 'Printer IP address',
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
              )
            else
              Row(
                children: [
                  FilledButton.tonalIcon(
                    onPressed: _scan,
                    icon: const Icon(Icons.search),
                    label: const Text('Find printers'),
                  ),
                  const SizedBox(width: 12),
                  Text('${_devices.length} found'),
                ],
              ),
            if (!isNetwork) ...[
              const SizedBox(height: 8),
              Expanded(
                flex: 2,
                child: _devices.isEmpty
                    ? const Center(child: Text('No devices yet.'))
                    : ListView.builder(
                        itemCount: _devices.length,
                        itemBuilder: (context, i) {
                          final device = _devices[i];
                          return RadioListTile<PrinterDevice>(
                            value: device,
                            groupValue: _selected,
                            onChanged: (value) =>
                                setState(() => _selected = value),
                            title: Text(device.name),
                            subtitle: Text(
                                'vendor ${device.vendorId} · product ${device.productId}'),
                          );
                        },
                      ),
              ),
            ],
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton(onPressed: _connect, child: const Text('Connect')),
                OutlinedButton(
                    onPressed: _disconnect, child: const Text('Disconnect')),
                OutlinedButton(
                    onPressed: _printSample, child: const Text('Print sample')),
                OutlinedButton(
                    onPressed: _readStatus, child: const Text('Read status')),
              ],
            ),
            const Divider(height: 24),
            Expanded(
              flex: 3,
              child: SingleChildScrollView(
                child: Text(
                  _log.isEmpty ? 'Nothing yet.' : _log,
                  style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

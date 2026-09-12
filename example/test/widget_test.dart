import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:flutter_pos_printer_example/main.dart';

void main() {
  testWidgets('opens on the USB tab with nothing connected', (tester) async {
    await tester.pumpWidget(const ExampleApp());

    expect(find.text('POS printer — USB-only fork'), findsOneWidget);
    expect(find.text('USB status: none'), findsOneWidget);
    expect(find.text('No devices yet.'), findsOneWidget);
  });

  testWidgets('offers USB and network, and nothing else', (tester) async {
    await tester.pumpWidget(const ExampleApp());

    // This fork has no Bluetooth: if a third transport ever shows up here,
    // either the enum grew or something was reintroduced by mistake.
    expect(find.widgetWithText(ButtonSegment, 'USB'), findsNothing);
    expect(find.text('USB'), findsOneWidget);
    expect(find.text('Network'), findsOneWidget);
    expect(find.textContaining('Bluetooth'), findsNothing);
  });

  testWidgets('switching to network asks for an IP instead of scanning',
      (tester) async {
    await tester.pumpWidget(const ExampleApp());

    await tester.tap(find.text('Network'));
    await tester.pumpAndSettle();

    expect(find.text('Printer IP address'), findsOneWidget);
    expect(find.text('Find printers'), findsNothing);
  });
}

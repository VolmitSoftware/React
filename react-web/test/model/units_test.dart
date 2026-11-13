library;

import 'package:test/test.dart';

import 'package:react_web/model/units.dart';

void main() {
  test('short rate units are spelled out and leave the label', () {
    expect(unitBeside('Spike rate', '/m'), '/min');
    expect(unitBeside('Handles', '/t'), '/tick');
    expect(labelWithoutUnit('Handles/Tick', '/t'), 'Handles');
    expect(labelWithoutUnit('Hoppers per tick', '/t'), 'Hoppers per tick');
    expect(unitBeside('Backlog growth', '/s'), '/s');
  });

  test('a negative zero reads as zero', () {
    expect(withoutNegativeZero('-0'), '0');
    expect(withoutNegativeZero('-0.0'), '0.0');
    expect(withoutNegativeZero('-0.5'), '-0.5');
    expect(withoutNegativeZero('10'), '10');
  });

  test('withUnit puts no space before % and one before other units', () {
    expect(withUnit('18.9', '%'), '18.9%');
    expect(withUnit('67', ' ms'), '67 ms');
    expect(withUnit('40', ''), '40');
    expect(withUnit('--', 'ms'), '--');
  });

  test('unitBeside drops a unit that repeats the label', () {
    expect(unitBeside('TPS', 'TPS'), isNull);
    expect(unitBeside('Worst TPS', 'tps'), isNull);
    expect(unitBeside('Tick time', 'ms'), 'ms');
  });

  test('a counted noun stays in the label and leaves the value bare', () {
    // The plugin's "Chunk Tickets" counts "tickets": "Chunk tickets 0",
    // never "Chunk 0 tickets"; "Chunks Force Loaded" counts "chunks".
    expect(labelWithoutUnit('Chunk Tickets', 'tickets'), 'Chunk Tickets');
    expect(unitBeside('Chunk Tickets', 'tickets'), isNull);
    expect(unitBeside('Chunks Force Loaded', 'chunks'), isNull);
    expect(unitBeside('Loaded chunk count', 'chunks'), isNull);
    expect(unitBeside('Save interval', 'ticks'), 'ticks');
  });

  test('labelWithoutUnit strips a trailing unit word', () {
    expect(labelWithoutUnit('Generation total ms', 'ms'), 'Generation total');
    expect(labelWithoutUnit('Projection render MS', 'ms'), 'Projection render');
    expect(labelWithoutUnit('ms', 'ms'), 'ms');
    expect(labelWithoutUnit('Players', ''), 'Players');
  });

  test('numbers group thousands and drop a trailing .0', () {
    expect(groupThousands(13357), '13,357');
    expect(groupThousands(812), '812');
    expect(compactNumber(15.0), '15');
    expect(compactNumber(18.46), '18.5');
  });

  test('sizes read from bytes, one decimal under 100', () {
    expect(byteSize(13357 * 1024 * 1024), ('13.0', 'GB'));
    expect(byteSize(812 * 1024 * 1024), ('812', 'MB'));
    expect(byteSize(83614412), ('79.7', 'MB'));
    expect(byteSize(512), ('512', 'B'));
    expect(humanBytes(4294967296), '4.0 GB');
  });
}

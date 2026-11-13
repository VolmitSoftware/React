/// One way to write a number with its unit everywhere: "18.9%", "67 ms",
/// "13.0 GB", "13,357". Labels never carry the unit the value shows.
library;

/// [value] followed by [unit]: no space before "%", one space otherwise.
String withUnit(String value, String? unit) {
  final String u = (unit ?? '').trim();
  if (u.isEmpty || value == '--') return value;
  if (u == '%') return '$value%';
  return '$value $u';
}

/// The plugin's short rate units spelled out: "/t" reads "/tick", "/m"
/// (per minute, not metre) "/min".
String _spelledOut(String unit) => switch (unit) {
  '/t' => '/tick',
  '/m' => '/min',
  _ => unit,
};

/// "-0" and "-0.0" (a rate that rounds to nothing) as "0" and "0.0".
String withoutNegativeZero(String value) =>
    RegExp(r'^-0(\.0+)?$').hasMatch(value) ? value.substring(1) : value;

/// [unit] unless it only repeats [label] ("TPS" under "TPS"), or names
/// what the label already counts ("chunks" under "Chunks force loaded").
String? unitBeside(String label, String? unit) {
  final String u = _spelledOut((unit ?? '').trim());
  if (u.isEmpty) return null;
  final String l = label.trim().toLowerCase();
  final String lower = u.toLowerCase();
  if (l == lower || l.endsWith(' $lower')) return null;
  if (_countsIn(l, lower)) return null;
  return u;
}

/// Whether [unit] is a counted noun ("tickets", "chunks") that [label]
/// already names, singular or plural.
bool _countsIn(String label, String unit) {
  if (!RegExp(r'^[a-z]{3,}$').hasMatch(unit)) return false;
  final String one = unit.endsWith('s')
      ? unit.substring(0, unit.length - 1)
      : unit;
  return RegExp('\\b(${RegExp.escape(one)}|${RegExp.escape(unit)})\\b')
      .hasMatch(label);
}

/// [label] without a trailing unit word that the value already shows
/// ("Generation total ms" with "ms" reads "Generation total"). A counted
/// noun stays: "Chunk tickets" counts tickets and shows no unit.
String labelWithoutUnit(String label, String? unit) {
  final String u = _spelledOut((unit ?? '').trim()).toLowerCase();
  final String trimmed = label.trim();
  // "Handles/Tick" over "/tick" reads "Handles".
  if (u.startsWith('/') && trimmed.toLowerCase().endsWith(u)) {
    return trimmed.substring(0, trimmed.length - u.length).trimRight();
  }
  if (u.isEmpty || u == '%' || _countsIn(trimmed.toLowerCase(), u)) {
    return trimmed;
  }
  final String lower = trimmed.toLowerCase();
  if (lower != u && lower.endsWith(' $u')) {
    return trimmed.substring(0, trimmed.length - u.length - 1).trimRight();
  }
  return trimmed;
}

/// Whole numbers with thousands separators ("13,357").
String groupThousands(num value) {
  final bool negative = value < 0;
  final String digits = value.abs().round().toString();
  final StringBuffer out = StringBuffer();
  for (int index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 == 0) out.write(',');
    out.write(digits[index]);
  }
  return negative ? '-$out' : out.toString();
}

/// A plain number with at most one decimal and no trailing ".0" ("15",
/// "18.5").
String compactNumber(double value) {
  if (value == value.roundToDouble()) return groupThousands(value);
  return value.toStringAsFixed(1);
}

/// The size units, a power of 1024 apart.
const List<String> kByteUnits = <String>['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

/// The power of 1024 a size of [bytes] is written in: 0 for B, 3 for GB.
int bytePower(double bytes) {
  double v = bytes.abs();
  int power = 0;
  while (v >= 1024.0 && power < kByteUnits.length - 1) {
    v /= 1024.0;
    power++;
  }
  return power;
}

/// [bytes] as a size: "812 MB", "13.0 GB", "79.7 MB" (one decimal under
/// 100 from KB on): (value, unit).
(String, String) byteSize(double bytes) {
  final int power = bytePower(bytes);
  final double v = bytes / bytesIn(power);
  final String text = power == 0
      ? groupThousands(v)
      : v.abs() >= 100.0
      ? groupThousands(v)
      : v.toStringAsFixed(1);
  return (text, kByteUnits[power]);
}

/// [bytes] as one string ("13.0 GB").
String humanBytes(double bytes) {
  final (String value, String unit) = byteSize(bytes);
  return withUnit(value, unit);
}

/// The bytes in one unit of [power] (1024 to the [power]).
double bytesIn(int power) {
  double factor = 1;
  for (int index = 0; index < power; index++) {
    factor *= 1024;
  }
  return factor;
}

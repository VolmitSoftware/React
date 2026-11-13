/// The unit shown after a sampler's value. The plugin's suffix is written
/// for the in-game monitor and often adds a tag that names the metric
/// ("ms CHGEN", "PLR", "GEN/s"); the page already names it, so only the unit
/// stays: "ms", "", "/s".
String metricUnit(String suffix) {
  final String trimmed = suffix.trim();
  if (trimmed.isEmpty) return '';
  final String first = trimmed.split(RegExp(r'\s+')).first;
  final RegExpMatch? rate = _taggedRate.firstMatch(first);
  if (rate != null) {
    return _byteUnits.contains(rate.group(1)) ? first : rate.group(2)!;
  }
  if (_tag.hasMatch(first) && !_byteUnits.contains(first)) return '';
  return first;
}

final RegExp _taggedRate = RegExp(r'^([A-Z]+)(/[a-z]+)$');
final RegExp _tag = RegExp(r'^[a-z]?[A-Z][A-Z0-9]+$');
const Set<String> _byteUnits = <String>{'B', 'KB', 'MB', 'GB', 'TB', 'PB'};

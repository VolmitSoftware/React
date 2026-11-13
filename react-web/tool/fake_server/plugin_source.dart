import 'dart:io';

/// Features and tweaks read from the plugin's own sources, so the fake server
/// lists the same controls, knobs, defaults and help text a real server does.
///
/// A control is a `Feature*.java` or `Tweak*.java` file with an
/// `ID = "..."` constant. Its description is the class's
/// `@ConfigDescription`; its knobs are the fields annotated with
/// `@ConfigDoc` whose type the plugin can edit (int, long, double, float,
/// boolean, String, or an enum declared in the same file), keyed by field
/// name like `KnobSerializer` does.
final class PluginControlSource {
  final List<Map<String, dynamic>> features;
  final List<Map<String, dynamic>> tweaks;

  const PluginControlSource({required this.features, required this.tweaks});

  /// Reads `<content>/feature` and `<content>/tweak` under the plugin's
  /// `art/arcane/react/content` source folder.
  static PluginControlSource read(Directory content) {
    if (!content.existsSync()) {
      throw StateError('Plugin sources not found at ${content.path}');
    }
    return PluginControlSource(
      features: _controls(Directory('${content.path}/feature'), 'Feature'),
      tweaks: _controls(Directory('${content.path}/tweak'), 'Tweak'),
    );
  }

  /// The plugin source folder next to this package (`../React/...`).
  static Directory defaultContent() {
    final String script = Platform.script.toFilePath();
    final Directory start = script.endsWith('.dart')
        ? File(script).parent
        : Directory.current;
    for (Directory dir = start; ; dir = dir.parent) {
      final Directory candidate = Directory(
        '${dir.path}/React/src/main/java/art/arcane/react/content',
      );
      if (candidate.existsSync()) return candidate;
      if (dir.parent.path == dir.path) break;
    }
    return Directory(
      '${Directory.current.parent.path}/React/src/main/java/art/arcane/react/content',
    );
  }

  static List<Map<String, dynamic>> _controls(Directory dir, String prefix) {
    if (!dir.existsSync()) return <Map<String, dynamic>>[];
    final List<File> files =
        dir
            .listSync()
            .whereType<File>()
            .where(
              (File file) =>
                  file.uri.pathSegments.last.startsWith(prefix) &&
                  file.path.endsWith('.java'),
            )
            .toList()
          ..sort((File a, File b) => a.path.compareTo(b.path));
    final List<Map<String, dynamic>> controls = <Map<String, dynamic>>[];
    for (final File file in files) {
      final String source = file.readAsStringSync();
      if (RegExp(r'public\s+abstract\s+class').hasMatch(source)) continue;
      final RegExpMatch? id = RegExp(
        r'public\s+static\s+final\s+String\s+ID\s*=\s*"([^"]+)"',
      ).firstMatch(source);
      if (id == null || id.group(1) == 'unknown') continue;
      final String controlId = id.group(1)!;
      controls.add(<String, dynamic>{
        'id': controlId,
        'name': _capitalizeWords(controlId.replaceAll('-', ' ')),
        'category': categoryFor(controlId),
        'enabled': true,
        'description': _annotationText(source, 'ConfigDescription') ?? '',
        'knobs': _inheritedKnobs(dir, source),
      });
    }
    return controls;
  }

  /// The knobs of [source] after those of the base classes declared in the
  /// same folder, the way `ConfigReflectionUtil.collectFields` walks them.
  static List<Map<String, dynamic>> _inheritedKnobs(
    Directory dir,
    String source,
  ) {
    final RegExpMatch? base = RegExp(r'class\s+\w+\s+extends\s+(\w+)')
        .firstMatch(source);
    final File parent = File('${dir.path}/${base?.group(1)}.java');
    return <Map<String, dynamic>>[
      if (base != null && parent.existsSync())
        ..._inheritedKnobs(dir, parent.readAsStringSync()),
      ..._knobs(source),
    ];
  }

  /// `ReactGuiTaxonomy.categoryForId`, English labels.
  static String categoryFor(String id) {
    final String n = id.toLowerCase();
    bool any(List<String> words) => words.any(n.contains);
    if (n.startsWith('plugin-')) return 'Plugins';
    if (any(<String>[
      'map',
      'overlay',
      'heatmap',
      '-pie',
      'pie-',
      'replay',
      'impact',
    ])) {
      return 'Diagnostics';
    }
    if (any(<String>['redstone', 'circuit'])) return 'Redstone';
    if (any(<String>['hopper'])) return 'Hoppers';
    if (any(<String>[
      'entity',
      'mob',
      'spawn',
      'item',
      'drop',
      'projectile',
      'vehicle',
      'minecart', //
      'orb', 'despawn', 'stack', 'trim', 'burst', 'farm', 'bubbler', 'crowd',
    ])) {
      return 'Entities';
    }
    if (any(<String>[
      'chunk', 'column', 'gravity', 'crop', 'leaf', 'portal', 'explosion', //
      'fluid', 'snow', 'fire', 'falling', 'quarantine', 'block',
    ])) {
      return 'World';
    }
    if (any(<String>[
      'view',
      'tick',
      'tps',
      'mspt',
      'budget',
      'governor',
      'activation',
      'sleep', //
      'surge',
      'incident',
      'hibernat',
      'save',
      'pathfinder',
      'tracker',
      'afk',
      'spike',
      'load',
    ])) {
      return 'Performance';
    }
    return 'Other';
  }

  static List<Map<String, dynamic>> _knobs(String source) {
    final Map<String, List<String>> enums = <String, List<String>>{
      for (final RegExpMatch match in RegExp(
        r'enum\s+(\w+)\s*\{([^;}]*)',
      ).allMatches(source))
        match.group(1)!: match
            .group(2)!
            .split(',')
            .map((String part) => part.trim().split(RegExp(r'[\s(]')).first)
            .where((String part) => RegExp(r'^[A-Z][A-Z0-9_]*$').hasMatch(part))
            .toList(),
    };
    final List<Map<String, dynamic>> knobs = <Map<String, dynamic>>[];
    final RegExp docAnnotation = RegExp(r'@(?:[\w.]+\.)?ConfigDoc\(');
    int from = 0;
    while (true) {
      final RegExpMatch? found = docAnnotation.firstMatch(
        source.substring(from),
      );
      if (found == null) break;
      final int open = from + found.end - 1;
      final int close = _closingParen(source, open);
      if (close < 0) break;
      from = close + 1;
      final String doc = _namedText(source.substring(open + 1, close), 'value');
      final Match? field = RegExp(
        r'(?:\s*@[\w.]+(?:\([^)]*\))?)*\s*((?:(?:private|protected|public|static|final|transient|volatile)\s+)*)([\w.]+(?:<[^>]*>)?)\s+(\w+)\s*(?:=\s*([^;]+))?;',
      ).matchAsPrefix(source, close + 1);
      if (field == null) continue;
      final String modifiers = field.group(1)!;
      if (modifiers.contains('static') || modifiers.contains('transient')) {
        continue;
      }
      final String javaType = field.group(2)!;
      final String name = field.group(3)!;
      final String raw = (field.group(4) ?? '').trim();
      final ({String type, Object? value, List<String> options})? knob =
          _classify(javaType, raw, enums);
      if (knob == null) continue;
      knobs.add(<String, dynamic>{
        'key': name,
        'label': _displayName(name),
        'type': knob.type,
        'value': knob.value,
        'options': knob.options,
        'doc': doc,
      });
    }
    return knobs;
  }

  static ({String type, Object? value, List<String> options})? _classify(
    String javaType,
    String raw,
    Map<String, List<String>> enums,
  ) {
    switch (javaType) {
      case 'int' || 'Integer' || 'long' || 'Long' || 'short' || 'Short':
        return (
          type: 'int',
          value: _number(raw)?.round() ?? 0,
          options: const <String>[],
        );
      case 'double' || 'Double' || 'float' || 'Float':
        return (
          type: 'double',
          value: _number(raw)?.toDouble() ?? 0.0,
          options: const <String>[],
        );
      case 'boolean' || 'Boolean':
        return (type: 'bool', value: raw == 'true', options: const <String>[]);
      case 'String':
        final RegExpMatch? text = RegExp(r'^"((?:[^"\\]|\\.)*)"$')
            .firstMatch(raw);
        return (
          type: 'string',
          value: text?.group(1) ?? '',
          options: const <String>[],
        );
    }
    final List<String>? options = enums[javaType];
    if (options == null || options.isEmpty) return null;
    final String constant = raw.split('.').last;
    return (
      type: 'enum',
      value: options.contains(constant) ? constant : options.first,
      options: options,
    );
  }

  /// A literal or a product of literals such as `15L * 60L * 1_000L`.
  static num? _number(String raw) {
    num? product;
    for (final String part in raw.split('*')) {
      final String literal = part
          .trim()
          .replaceAll('_', '')
          .replaceAll(RegExp(r'[LlDdFf]$'), '');
      final num? value = num.tryParse(literal);
      if (value == null) return null;
      product = (product ?? 1) * value;
    }
    return product;
  }

  static String? _annotationText(String source, String annotation) {
    final RegExpMatch? found = RegExp('@(?:[\\w.]+\\.)?$annotation\\(')
        .firstMatch(source);
    if (found == null) return null;
    final int open = found.end - 1;
    final int close = _closingParen(source, open);
    if (close < 0) return null;
    return _namedText(source.substring(open + 1, close), 'value');
  }

  /// The string literals of argument [name] (or the unnamed first argument),
  /// joined as Java would concatenate them.
  static String _namedText(String arguments, String name) {
    for (final String argument in _splitTopLevel(arguments)) {
      final RegExpMatch? named = RegExp(r'^\s*(\w+)\s*=').firstMatch(argument);
      if (named != null && named.group(1) != name) continue;
      return RegExp(r'"((?:[^"\\]|\\.)*)"')
          .allMatches(argument)
          .map((RegExpMatch match) => match.group(1)!.replaceAll(r'\"', '"'))
          .join();
    }
    return '';
  }

  static List<String> _splitTopLevel(String text) {
    final List<String> parts = <String>[];
    int depth = 0;
    bool quoted = false;
    int start = 0;
    for (int i = 0; i < text.length; i++) {
      final String c = text[i];
      if (quoted) {
        if (c == r'\') {
          i++;
        } else if (c == '"') {
          quoted = false;
        }
        continue;
      }
      if (c == '"') quoted = true;
      if (c == '(' || c == '{') depth++;
      if (c == ')' || c == '}') depth--;
      if (c == ',' && depth == 0) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  static int _closingParen(String source, int open) {
    int depth = 0;
    bool quoted = false;
    for (int i = open; i < source.length; i++) {
      final String c = source[i];
      if (quoted) {
        if (c == r'\') {
          i++;
        } else if (c == '"') {
          quoted = false;
        }
        continue;
      }
      if (c == '"') {
        quoted = true;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) return i;
      }
    }
    return -1;
  }

  /// `ConfigReflectionUtil.displayName`.
  static String _displayName(String key) {
    final String spaced = key
        .replaceAll('_', ' ')
        .replaceAll('-', ' ')
        .replaceAllMapped(
          RegExp(r'([a-z])([A-Z])'),
          (Match m) => '${m[1]} ${m[2]}',
        )
        .trim();
    if (spaced.isEmpty) return key;
    return '${spaced[0].toUpperCase()}${spaced.substring(1)}';
  }

  static String _capitalizeWords(String text) => text
      .split(' ')
      .where((String word) => word.isNotEmpty)
      .map((String word) => '${word[0].toUpperCase()}${word.substring(1)}')
      .join(' ');
}

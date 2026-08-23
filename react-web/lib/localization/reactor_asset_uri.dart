library;

Uri resolveReactorWebAssetUri(Uri base, String location) {
  final Uri parsed = Uri.parse(location);
  if (parsed.hasScheme) return parsed;
  if (location.startsWith('//')) return base.resolve(location);
  return base.resolve(location.startsWith('/') ? location : '/$location');
}

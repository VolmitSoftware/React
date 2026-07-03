// dart format off
// ignore_for_file: type=lint

// GENERATED FILE, DO NOT MODIFY
// Generated with jaspr_builder

import 'package:jaspr/server.dart';
import 'package:arcane_jaspr/component/collection/card_carousel.dart'
    as _card_carousel;
import 'package:arcane_jaspr/component/collection/infinite_carousel.dart'
    as _infinite_carousel;
import 'package:arcane_jaspr/component/form/field.dart' as _field;
import 'package:arcane_jaspr/component/view/avatar.dart' as _avatar;

/// Default [ServerOptions] for use with your Jaspr project.
///
/// Use this to initialize Jaspr **before** calling [runApp].
///
/// Example:
/// ```dart
/// import 'main.server.options.dart';
///
/// void main() {
///   Jaspr.initializeApp(
///     options: defaultServerOptions,
///   );
///
///   runApp(...);
/// }
/// ```
ServerOptions get defaultServerOptions => ServerOptions(
  clientId: 'main.client.dart.js',
  styles: () => [
    ..._card_carousel.ArcaneHeroCarousel.styles,
    ..._card_carousel.ArcaneNavigableCarousel.styles,
    ..._card_carousel.CardCarousel.styles,
    ..._infinite_carousel.ArcaneInfiniteCarousel.styles,
    ..._field.ArcaneFieldStyles.styles,
    ..._avatar.ArcaneAvatarBadge.styles,
  ],
);

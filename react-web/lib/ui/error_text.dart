/// A request error as one line for the person using the page.
library;

import '../localization/reactor_localizations.dart';
import '../service/react_exceptions.dart';

String localizedReactorError(Object error) => switch (error) {
  ReactAuthException() => reactorText(ReactorText.errorAuthentication),
  ReactUnavailable() => reactorText(ReactorText.errorUnavailable),
  ReactBadRequest() => reactorText(ReactorText.errorBadRequest),
  ReactForbidden() => reactorText(ReactorText.errorForbidden),
  ReactNotFound() => reactorText(ReactorText.errorNotFound),
  ReactConflict() => reactorText(ReactorText.errorConflict),
  _ => reactorText(ReactorText.errorUnexpected),
};

library;

abstract final class RelayIdentity {
  /// Whether a server answering as [identityServerId] is the one the entry
  /// was paired with; an entry without a pin accepts any.
  static bool pinMatches({
    required String? pinnedFingerprint,
    required String identityServerId,
  }) {
    if (pinnedFingerprint == null) return true;
    return pinnedFingerprint == identityServerId;
  }
}

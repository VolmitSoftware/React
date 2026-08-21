package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperations;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProtectionClaims {
  public static final String CLAIM_PREFIX = "protect-";

  private ProtectionClaims() {
  }

  public static NamespacedKey keyFor(String namespace, String ownerToken) {
    if (namespace == null || namespace.isEmpty() || ownerToken == null || ownerToken.isEmpty()) {
      return null;
    }

    return new NamespacedKey(namespace, CLAIM_PREFIX + ownerToken);
  }

  public static Scan scan(PersistentDataContainer container, String namespace) {
    if (container == null || container.isEmpty()) {
      return Scan.EMPTY;
    }

    int mask = ReactOperations.NONE;
    List<String> owners = null;

    for (NamespacedKey key : container.getKeys()) {
      if (!namespace.equals(key.getNamespace()) || !key.getKey().startsWith(CLAIM_PREFIX)) {
        continue;
      }

      Integer stored = container.get(key, PersistentDataType.INTEGER);

      if (stored == null) {
        continue;
      }

      int claimed = ReactOperations.sanitize(stored);

      if (claimed == ReactOperations.NONE) {
        continue;
      }

      mask |= claimed;

      if (owners == null) {
        owners = new ArrayList<>(2);
      }

      owners.add(key.getKey().substring(CLAIM_PREFIX.length()));
    }

    if (owners == null) {
      return Scan.EMPTY;
    }

    Collections.sort(owners);
    return new Scan(mask, String.join(",", owners));
  }

  public static int add(PersistentDataContainer container, NamespacedKey key, int operations) {
    Integer existing = container.get(key, PersistentDataType.INTEGER);
    int merged = ReactOperations.sanitize((existing == null ? 0 : existing) | operations);
    container.set(key, PersistentDataType.INTEGER, merged);
    return merged;
  }

  public static boolean remove(PersistentDataContainer container, NamespacedKey key) {
    if (!container.has(key, PersistentDataType.INTEGER)) {
      return false;
    }

    container.remove(key);
    return true;
  }

  public record Scan(int mask, String owners) {
    public static final Scan EMPTY = new Scan(ReactOperations.NONE, "");
  }
}

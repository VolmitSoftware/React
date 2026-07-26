package art.arcane.react.api.protect;

import java.util.EnumSet;
import java.util.Set;

public final class ReactOperations {
  public static final int NONE = 0;

  private static final ReactOperation[] VALUES = ReactOperation.values();
  private static final int ALL = (1 << VALUES.length) - 1;

  private ReactOperations() {
  }

  public static int of(ReactOperation... operations) {
    if (operations == null) {
      return NONE;
    }

    int mask = NONE;

    for (ReactOperation operation : operations) {
      if (operation != null) {
        mask |= 1 << operation.ordinal();
      }
    }

    return mask;
  }

  public static int of(Set<ReactOperation> operations) {
    if (operations == null) {
      return NONE;
    }

    int mask = NONE;

    for (ReactOperation operation : operations) {
      if (operation != null) {
        mask |= 1 << operation.ordinal();
      }
    }

    return mask;
  }

  public static int all() {
    return ALL;
  }

  public static int sanitize(int operations) {
    return operations & ALL;
  }

  public static boolean covers(int operations, ReactOperation operation) {
    return operation != null && (operations & (1 << operation.ordinal())) != 0;
  }

  public static Set<ReactOperation> expand(int operations) {
    int mask = operations & ALL;

    if (mask == NONE) {
      return Set.of();
    }

    EnumSet<ReactOperation> out = EnumSet.noneOf(ReactOperation.class);

    for (ReactOperation operation : VALUES) {
      if ((mask & (1 << operation.ordinal())) != 0) {
        out.add(operation);
      }
    }

    return Set.copyOf(out);
  }
}

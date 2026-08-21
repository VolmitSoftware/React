package art.arcane.react.api.protect.internal;

public final class ProtectionInstaller {
  private static volatile ProtectionBinding installed;

  private ProtectionInstaller() {
  }

  public static void install(ProtectionBinding binding) {
    installed = binding;
  }

  public static void uninstall(ProtectionBinding binding) {
    if (installed == binding) {
      installed = null;
    }
  }

  public static ProtectionBinding binding() {
    return installed;
  }
}

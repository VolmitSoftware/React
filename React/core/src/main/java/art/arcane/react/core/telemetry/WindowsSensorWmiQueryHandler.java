package art.arcane.react.core.telemetry;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import oshi.SystemInfo;
import oshi.util.platform.windows.WmiQueryHandler;

import java.util.Locale;

public final class WindowsSensorWmiQueryHandler extends WmiQueryHandler {
  private static final String ACPI_THERMAL_ZONE_CLASS = "MSAcpi_ThermalZoneTemperature";
  private static final String LIBRE_HARDWARE_MONITOR_CONFIG_CLASS =
      "io.github.pandalxb.jlibrehardwaremonitor.config.ComputerConfig";
  private static final int QUIET_MISSING_LIBRE_MAJOR_VERSION = 6;
  private static final int QUIET_MISSING_LIBRE_MINOR_VERSION = 11;
  private static final int WBEM_E_NOT_SUPPORTED = 0x8004100C;

  public WindowsSensorWmiQueryHandler() {
  }

  public static void installIfWindows() {
    if (isWindows(System.getProperty("os.name", ""))) {
      WmiQueryHandler.setInstanceClass(WindowsSensorWmiQueryHandler.class);
    }
  }

  public static boolean shouldQuerySensors() {
    String operatingSystemName = System.getProperty("os.name", "");
    Package oshiPackage = SystemInfo.class.getPackage();
    String oshiVersion = oshiPackage == null ? null : oshiPackage.getImplementationVersion();
    return shouldQuerySensors(
        operatingSystemName,
        oshiVersion,
        isLibreHardwareMonitorAvailable(SystemInfo.class.getClassLoader())
    );
  }

  @Override
  protected void handleComException(WmiQuery<?> query, COMException failure) {
    Integer errorCode = failure.getHresult() == null ? null : failure.getHresult().intValue();
    if (shouldSuppress(query.getWmiClassName(), errorCode)) {
      return;
    }
    super.handleComException(query, failure);
  }

  static boolean shouldSuppress(String wmiClassName, Integer errorCode) {
    return ACPI_THERMAL_ZONE_CLASS.equals(wmiClassName)
        && errorCode != null
        && errorCode == WBEM_E_NOT_SUPPORTED;
  }

  static boolean shouldQuerySensors(
      String operatingSystemName,
      String oshiVersion,
      boolean libreHardwareMonitorAvailable
  ) {
    if (!isWindows(operatingSystemName) || libreHardwareMonitorAvailable) {
      return true;
    }
    return hasQuietMissingLibreHandling(oshiVersion);
  }

  private static boolean isWindows(String operatingSystemName) {
    return operatingSystemName != null
        && operatingSystemName.toLowerCase(Locale.ROOT).contains("windows");
  }

  private static boolean isLibreHardwareMonitorAvailable(ClassLoader classLoader) {
    try {
      Class.forName(LIBRE_HARDWARE_MONITOR_CONFIG_CLASS, false, classLoader);
      return true;
    } catch (ClassNotFoundException | LinkageError failure) {
      return false;
    }
  }

  private static boolean hasQuietMissingLibreHandling(String version) {
    if (version == null) {
      return false;
    }
    int firstSeparator = version.indexOf('.');
    if (firstSeparator <= 0) {
      return false;
    }
    int secondSeparator = version.indexOf('.', firstSeparator + 1);
    String minorVersion = secondSeparator < 0
        ? version.substring(firstSeparator + 1)
        : version.substring(firstSeparator + 1, secondSeparator);
    try {
      int major = Integer.parseInt(version.substring(0, firstSeparator));
      int minor = Integer.parseInt(minorVersion);
      return major > QUIET_MISSING_LIBRE_MAJOR_VERSION
          || major == QUIET_MISSING_LIBRE_MAJOR_VERSION
          && minor >= QUIET_MISSING_LIBRE_MINOR_VERSION;
    } catch (NumberFormatException failure) {
      return false;
    }
  }
}

package art.arcane.react.core.telemetry;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import oshi.util.platform.windows.WmiQueryHandler;

import java.util.Locale;

public final class WindowsSensorWmiQueryHandler extends WmiQueryHandler {
  private static final String ACPI_THERMAL_ZONE_CLASS = "MSAcpi_ThermalZoneTemperature";
  private static final int WBEM_E_NOT_SUPPORTED = 0x8004100C;

  public WindowsSensorWmiQueryHandler() {
  }

  public static void installIfWindows() {
    String operatingSystemName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (operatingSystemName.contains("windows")) {
      WmiQueryHandler.setInstanceClass(WindowsSensorWmiQueryHandler.class);
    }
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
}

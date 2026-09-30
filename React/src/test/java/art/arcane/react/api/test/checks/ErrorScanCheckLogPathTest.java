package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Method;

class ErrorScanCheckLogPathTest {
  @Test
  void relativePluginDataFolderResolvesTheServerLatestLog() throws ReflectiveOperationException {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFolder()).thenReturn(new File("plugins", "React"));
    React.instance = plugin;
    try {
      Method serverLogFile = ErrorScanCheck.class.getDeclaredMethod("serverLogFile");
      serverLogFile.setAccessible(true);

      File resolved = (File) serverLogFile.invoke(new ErrorScanCheck());

      Assertions.assertEquals(new File("logs", "latest.log").getAbsoluteFile(), resolved);
    } finally {
      React.instance = previous;
    }
  }
}

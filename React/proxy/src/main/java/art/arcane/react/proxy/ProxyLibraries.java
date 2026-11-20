package art.arcane.react.proxy;

import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.app.builder.InjectingApplicationBuilder;
import io.github.slimjar.injector.loader.Injectable;
import io.github.slimjar.logging.ProcessLogger;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Downloads React's libraries (Javalin, Jackson, OSHI and the rest listed in {@code slimjar.dat}) into
 * {@code libraries/} under the data folder and adds them to React's class loader, as the Bukkit plugin does at load.
 */
public final class ProxyLibraries {
  private ProxyLibraries() {
  }

  /** {@code injectable} adds one library to React's class loader; null uses the class loader React loaded from. */
  public static void load(Path dataFolder, Injectable injectable, Logger logger) {
    ProcessLogger log = new ProcessLogger() {
      @Override
      public void info(String message, Object... arguments) {
        logger.fine(String.format(message, arguments));
      }

      @Override
      public void error(String message, Object... arguments) {
        logger.warning(String.format(message, arguments));
      }
    };
    InjectingApplicationBuilder<?> builder = injectable == null
        ? InjectingApplicationBuilder.create("React", ProxyLibraries.class.getClassLoader())
        : ApplicationBuilder.injecting("React", injectable);
    builder.downloadDirectoryPath(dataFolder.resolve("libraries")).logger(log).build();
  }
}

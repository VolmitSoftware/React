package art.arcane.react.testutil;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiled main classes live in two places: the Bukkit module's classes directory and the core module, which tests
 * see as a directory or a jar. Each root returned here can be walked with {@link Files}.
 */
public final class MainClassRoots {
  private static List<Path> roots;

  private MainClassRoots() {
  }

  public static synchronized List<Path> all() throws IOException, URISyntaxException {
    if (roots == null) {
      List<Path> found = new ArrayList<>();
      for (Class<?> anchor : List.of(React.class, J.class)) {
        Path location = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
          found.add(location);
        } else {
          FileSystem archive = FileSystems.newFileSystem(location, Map.of());
          found.add(archive.getPath("/"));
        }
      }
      roots = List.copyOf(found);
    }
    return roots;
  }
}

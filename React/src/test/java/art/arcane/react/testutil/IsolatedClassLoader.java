package art.arcane.react.testutil;

import java.io.IOException;
import java.io.InputStream;

public final class IsolatedClassLoader extends ClassLoader {
  private final String isolatedName;

  public IsolatedClassLoader(Class<?> isolated) {
    super(isolated.getClassLoader());
    this.isolatedName = isolated.getName();
  }

  public Class<?> isolatedClass() throws ClassNotFoundException {
    return loadClass(isolatedName);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (!name.equals(isolatedName) && !name.startsWith(isolatedName + "$")) {
      return super.loadClass(name, resolve);
    }

    synchronized (getClassLoadingLock(name)) {
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        byte[] bytes = readClassBytes(name);
        loaded = defineClass(name, bytes, 0, bytes.length);
      }
      if (resolve) {
        resolveClass(loaded);
      }
      return loaded;
    }
  }

  private byte[] readClassBytes(String name) throws ClassNotFoundException {
    String resource = name.replace('.', '/') + ".class";
    try (InputStream stream = getParent().getResourceAsStream(resource)) {
      if (stream == null) {
        throw new ClassNotFoundException(name);
      }
      return stream.readAllBytes();
    } catch (IOException exception) {
      throw new ClassNotFoundException(name, exception);
    }
  }
}

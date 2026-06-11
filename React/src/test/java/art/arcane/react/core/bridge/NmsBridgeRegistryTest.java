package art.arcane.react.core.bridge;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;

public class NmsBridgeRegistryTest {
  private static final class PrivateFieldHolder {
    private int value = 0;
    PrivateFieldHolder(int v) { this.value = v; }
  }

  private NmsBridgeRegistry registry;

  @Before
  public void setup() {
    registry = new NmsBridgeRegistry();
  }

  @Test
  public void resolvePublicStaticMethodAndInvoke() throws Throwable {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "Integer.parseInt",
        BridgeKind.STATIC_METHOD,
        List.of("java.lang.Integer"),
        "parseInt",
        List.of(List.of("java.lang.String")),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue(handle.available());
    int result = (int) handle.methodHandle().invokeWithArguments("42");
    Assert.assertEquals(42, result);
  }

  @Test
  public void unavailableDescriptorReturnsUnavailableHandle() {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "does.not.Exist.method",
        BridgeKind.METHOD,
        List.of("does.not.Exist"),
        "someMethod",
        List.of(List.of()),
        "void",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertFalse(handle.available());
    Assert.assertNotNull(handle.resolution().failureReason());
    Assert.assertFalse(handle.resolution().failureReason().isEmpty());
  }

  @Test
  public void repeatResolveReturnsSameInstance() {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "Integer.parseInt",
        BridgeKind.STATIC_METHOD,
        List.of("java.lang.Integer"),
        "parseInt",
        List.of(List.of("java.lang.String")),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle first = registry.resolve(descriptor);
    NmsBridgeHandle second = registry.resolve(descriptor);
    Assert.assertSame(first, second);
  }

  @Test
  public void resolvePublicVirtualMethod() throws Throwable {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "String.length",
        BridgeKind.METHOD,
        List.of("java.lang.String"),
        "length",
        List.of(List.of()),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue(handle.available());
    int len = (int) handle.methodHandle().invokeWithArguments("hello");
    Assert.assertEquals(5, len);
  }

  @Test
  public void resolveMethodInheritedFromSuperclass() throws Throwable {
    // LinkedHashMap does not declare isEmpty(); it inherits it from HashMap.
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "LinkedHashMap.isEmpty",
        BridgeKind.METHOD,
        List.of("java.util.LinkedHashMap"),
        "isEmpty",
        List.of(List.of()),
        "boolean",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue(handle.available());
    boolean empty = (boolean) handle.methodHandle().invokeWithArguments(new java.util.LinkedHashMap<String, String>());
    Assert.assertTrue(empty);
  }

  @Test
  public void resolveMethodDeclaredOnlyOnInterface() throws Throwable {
    // ArrayList does not declare stream(); it is a default method on Collection.
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "ArrayList.stream",
        BridgeKind.METHOD,
        List.of("java.util.ArrayList"),
        "stream",
        List.of(List.of()),
        "java.util.stream.Stream",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue(handle.available());
    Object stream = handle.methodHandle().invokeWithArguments((Object) new java.util.ArrayList<String>());
    Assert.assertNotNull(stream);
  }

  @Test
  public void resolvePublicField() throws Throwable {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "Integer.MAX_VALUE",
        BridgeKind.STATIC_FIELD,
        List.of("java.lang.Integer"),
        "MAX_VALUE",
        List.of(),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue(handle.available());
    int val = (int) handle.varHandle().get();
    Assert.assertEquals(Integer.MAX_VALUE, val);
  }

  @Test
  public void resolvePrivateField() throws Throwable {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "PrivateFieldHolder.value",
        BridgeKind.FIELD,
        List.of(PrivateFieldHolder.class.getName()),
        "value",
        List.of(),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle handle = registry.resolve(descriptor);
    Assert.assertTrue("Expected private field PrivateFieldHolder.value to resolve via setAccessible", handle.available());
    PrivateFieldHolder holder = new PrivateFieldHolder(99);
    int val = (int) handle.varHandle().get(holder);
    Assert.assertEquals(99, val);
  }

  @Test
  public void clearReleasesAllHandles() {
    NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
        "Integer.parseInt",
        BridgeKind.STATIC_METHOD,
        List.of("java.lang.Integer"),
        "parseInt",
        List.of(List.of("java.lang.String")),
        "int",
        Optional.empty()
    );
    NmsBridgeHandle before = registry.resolve(descriptor);
    registry.clear();
    NmsBridgeHandle after = registry.resolve(descriptor);
    Assert.assertNotSame("After clear, resolve should produce a new instance", before, after);
  }
}

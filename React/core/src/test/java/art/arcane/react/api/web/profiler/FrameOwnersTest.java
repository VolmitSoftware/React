package art.arcane.react.api.web.profiler;

import art.arcane.react.platform.ProfilerHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class FrameOwnersTest {
  @Test
  void jdkFramesAreJavaAndOthersAskTheHostOnce() {
    AtomicInteger lookups = new AtomicInteger();
    ProfilerHost host = new ProfilerHost() {
      @Override
      public Optional<Thread> serverThread() {
        return Optional.empty();
      }

      @Override
      public Optional<FrameOwner> ownerOf(String className, String methodName) {
        lookups.incrementAndGet();
        return className.equals("com.example.Mod") ? Optional.of(new FrameOwner("example", "Example", false)) : Optional.empty();
      }
    };
    FrameOwners owners = new FrameOwners(() -> Optional.of(host));
    Assertions.assertEquals(FrameOwners.JAVA, owners.ownerOf("java.util.HashMap", "get"));
    Assertions.assertEquals("example", owners.ownerOf("com.example.Mod$$Lambda/0x0000123", "run").id());
    Assertions.assertEquals("example", owners.ownerOf("com.example.Mod$$Lambda/0x0000123", "apply").id());
    Assertions.assertEquals(FrameOwners.MINECRAFT, owners.ownerOf("net.minecraft.Server", "tick"));
    Assertions.assertEquals(2, lookups.get());
  }

  @Test
  void lookupNameStripsLambdaAndHiddenSuffixes() {
    Assertions.assertEquals("a.B", FrameOwners.lookupName("a.B$$Lambda/0x1"));
    Assertions.assertEquals("a.B$Inner", FrameOwners.lookupName("a.B$Inner"));
    Assertions.assertEquals("a.B", FrameOwners.lookupName("a.B/0x2"));
  }

  @Test
  void noHostMeansMinecraft() {
    Assertions.assertEquals(FrameOwners.MINECRAFT, new FrameOwners(Optional::empty).ownerOf("com.example.Mod", "run"));
  }
}

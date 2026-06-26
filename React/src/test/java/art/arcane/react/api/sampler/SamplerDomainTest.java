package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.content.sampler.SamplerGcPauseP95;
import art.arcane.react.content.sampler.SamplerGcTimePercent;
import art.arcane.react.content.sampler.SamplerMemoryFree;
import art.arcane.react.content.sampler.SamplerMemoryUsed;
import art.arcane.react.content.sampler.SamplerUnknown;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.Supplier;
import java.util.stream.Stream;

class SamplerDomainTest {

  static Stream<Arguments> contentSamplers() {
    return Stream.of(
        Arguments.of("memory-used", (Supplier<Sampler>) SamplerMemoryUsed::new),
        Arguments.of("memory-free", (Supplier<Sampler>) SamplerMemoryFree::new),
        Arguments.of("gc-time-percent", (Supplier<Sampler>) SamplerGcTimePercent::new),
        Arguments.of("gc-pause-p95", (Supplier<Sampler>) SamplerGcPauseP95::new),
        Arguments.of("unknown", (Supplier<Sampler>) SamplerUnknown::new)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contentSamplers")
  void contentSamplerProducesFiniteNonNegativeValue(String id, Supplier<Sampler> factory) {
    double value;

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Sampler sampler = factory.get();
      sampler.start();
      value = sampler.sample();
      sampler.stop();
    }

    Assertions.assertTrue(Double.isFinite(value), id + " produced a non-finite value: " + value);
    Assertions.assertTrue(value >= 0.0D, id + " produced a negative value: " + value);
  }
}

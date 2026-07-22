package art.arcane.react.content.sampler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SamplerTickTimeTest {
  @Test
  void samplesPaperAverageTickTime() {
    Server server = mock(Server.class);
    when(server.getAverageTickTime()).thenReturn(37.5D);
    SamplerTickTime sampler = new SamplerTickTime();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);

      assertEquals(37.5D, sampler.onSample());
    }
  }

  @Test
  void invalidServerTickTimeCannotSignalPressure() {
    Server server = mock(Server.class);
    SamplerTickTime sampler = new SamplerTickTime();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      when(server.getAverageTickTime()).thenReturn(Double.NaN);
      assertEquals(0D, sampler.onSample());

      when(server.getAverageTickTime()).thenReturn(-5D);
      assertEquals(0D, sampler.onSample());
    }
  }
}

package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

class WebControllerListenerBindingTest {
  private WebController controller;

  @AfterEach
  void tearDown() {
    if (controller != null) {
      controller.stop();
    }
  }

  @Test
  void defaultWildcardAcceptsIpv4AndIpv6Loopback(@TempDir File dataFolder) throws Exception {
    WebConfiguration configuration = new WebConfiguration();
    configuration.setPort(0);
    controller = controller(configuration, dataFolder);

    controller.start();
    controller.postStart();
    controller.awaitStart(5000L);

    Assertions.assertEquals("::", configuration.getListenAddress());
    assertPing("http://127.0.0.1:" + controller.getBoundPort());
    assertPing("http://[::1]:" + controller.getBoundPort());
  }

  private WebController controller(WebConfiguration configuration, File dataFolder) {
    WebController target = new WebController() {
      @Override
      protected void executeAsync(Runnable runnable) {
        Thread.ofVirtual().start(runnable);
      }

      @Override
      protected IdentityDto resolveIdentity() {
        IdentityDto identity = new IdentityDto();
        identity.version = "listener-test";
        identity.serverName = "ListenerTest";
        identity.folia = false;
        identity.serverId = "[::1]:0";
        return identity;
      }
    };
    target.setConfig(configuration);
    target.setDataFolder(dataFolder);
    target.setSampleController(emptySampleController());
    return target;
  }

  @SuppressWarnings("unchecked")
  private SampleController emptySampleController() {
    SampleController sampleController = Mockito.mock(SampleController.class);
    Registry<Sampler> registry = Mockito.mock(Registry.class);
    Mockito.when(sampleController.getSamplers()).thenReturn(registry);
    Mockito.when(registry.all()).thenReturn(List.of());
    return sampleController;
  }

  private void assertPing(String baseUrl) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/v1/ping"))
        .GET()
        .build();
    HttpResponse<String> response = HttpClient.newHttpClient().send(
        request,
        HttpResponse.BodyHandlers.ofString()
    );
    Assertions.assertEquals(200, response.statusCode(), baseUrl);
  }
}

package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.PluginApiCatalogDto;
import art.arcane.react.api.web.dto.PluginApiValidationResultDto;
import art.arcane.react.api.web.resource.PluginApiPackResource;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackState;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginApiPackResourceTest {
  @Test
  void listMapsCatalogStatusAndValidationErrors() {
    PluginApiPackController controller = mock(PluginApiPackController.class);
    when(controller.packFolder()).thenReturn(Path.of("/plugins/React/plugin-apis"));
    when(controller.statuses()).thenReturn(List.of(status()));
    when(controller.validationErrors()).thenReturn(Map.of("broken.toml", "bad schema"));
    PluginApiPackResource resource = new PluginApiPackResource(() -> controller, mock(WebMutationReporter.class));
    Context context = mock(Context.class);
    ArgumentCaptor<Object> response = ArgumentCaptor.forClass(Object.class);

    resource.list(context);

    verify(context).json(response.capture());
    @SuppressWarnings("unchecked")
    Envelope<PluginApiCatalogDto> envelope = (Envelope<PluginApiCatalogDto>) response.getValue();
    assertEquals(Path.of("/plugins/React/plugin-apis").toString(), envelope.data().folder());
    assertEquals("community.example", envelope.data().packs()[0].id());
    assertEquals("broken.toml", envelope.data().errors()[0].fileName());
  }

  @Test
  void validateRequiresAdminAndReturnsParserResult() {
    PluginApiPackController controller = mock(PluginApiPackController.class);
    when(controller.validate("pack")).thenReturn(
        new PluginApiPackController.ValidationResult(true, "community.example", 1, "valid")
    );
    PluginApiPackResource resource = new PluginApiPackResource(() -> controller, mock(WebMutationReporter.class));
    Context context = mock(Context.class);
    PairingToken token = mock(PairingToken.class);
    when(token.hasScope("admin")).thenReturn(true);
    when(context.<PairingToken>attribute("token")).thenReturn(token);
    when(context.bodyAsClass(PluginApiPackResource.ContentBody.class)).thenReturn(
        new PluginApiPackResource.ContentBody("pack")
    );
    ArgumentCaptor<Object> response = ArgumentCaptor.forClass(Object.class);

    resource.validate(context);

    verify(context).json(response.capture());
    @SuppressWarnings("unchecked")
    Envelope<PluginApiValidationResultDto> envelope =
        (Envelope<PluginApiValidationResultDto>) response.getValue();
    assertEquals(true, envelope.data().valid());
    assertEquals("community.example", envelope.data().id());
  }

  @Test
  void installRejectsNonAdminBeforeWriting() throws IOException {
    PluginApiPackController controller = mock(PluginApiPackController.class);
    PluginApiPackResource resource = new PluginApiPackResource(() -> controller, mock(WebMutationReporter.class));
    Context context = mock(Context.class);
    PairingToken token = mock(PairingToken.class);
    when(token.hasScope("admin")).thenReturn(false);
    when(context.<PairingToken>attribute("token")).thenReturn(token);

    assertThrows(ForbiddenResponse.class, () -> resource.put(context));
    verify(controller, org.mockito.Mockito.never()).install(any(), any());
  }

  private PackStatus status() {
    return new PackStatus(
        "community.example",
        "1.0.0",
        "Example",
        List.of("Tests"),
        "Example",
        "1.0.0",
        List.of("*"),
        true,
        false,
        PackState.HEALTHY,
        "all-metrics-available",
        "community.example.toml",
        "pack",
        List.of()
    );
  }
}

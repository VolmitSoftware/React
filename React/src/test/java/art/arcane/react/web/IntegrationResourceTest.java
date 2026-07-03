package art.arcane.react.web;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IntegrationsDto;
import art.arcane.react.api.web.resource.IntegrationResource;
import art.arcane.react.core.controller.IntegrationController.Health;
import art.arcane.react.core.controller.IntegrationController.IntegrationStatus;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IntegrationResourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void it_returns_envelope_with_mapped_integrations_and_timeline() {
        Supplier<List<IntegrationStatus>> statusSupplier = mock(Supplier.class);
        IntFunction<List<String>> timelineSupplier = mock(IntFunction.class);
        when(statusSupplier.get()).thenReturn(List.of(
            new IntegrationStatus("iris", Health.HEALTHY, "v1", 123L, "ok"),
            new IntegrationStatus("adapt", Health.MISSING, "-", 0L, "not-discovered")
        ));
        when(timelineSupplier.apply(20)).thenReturn(List.of("12s ago - x"));

        IntegrationResource resource = new IntegrationResource(statusSupplier, timelineSupplier);
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn(null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.get(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<IntegrationsDto> envelope = (Envelope<IntegrationsDto>) captor.getValue();
        IntegrationsDto dto = envelope.data();
        assertEquals(2, dto.integrations.length);
        assertEquals("iris", dto.integrations[0].pluginId);
        assertEquals("HEALTHY", dto.integrations[0].health);
        assertEquals("MISSING", dto.integrations[1].health);
        assertEquals(1, dto.timeline.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void it_defaults_limit_to_20_when_absent_and_clamps() {
        Supplier<List<IntegrationStatus>> statusSupplier = mock(Supplier.class);
        IntFunction<List<String>> timelineSupplier = mock(IntFunction.class);
        when(statusSupplier.get()).thenReturn(List.of());
        when(timelineSupplier.apply(anyInt())).thenReturn(List.of());

        IntegrationResource resource = new IntegrationResource(statusSupplier, timelineSupplier);

        Context ctx1 = mock(Context.class);
        when(ctx1.queryParam("limit")).thenReturn(null);
        resource.get(ctx1);
        verify(timelineSupplier).apply(20);

        Context ctx2 = mock(Context.class);
        when(ctx2.queryParam("limit")).thenReturn("500");
        resource.get(ctx2);
        verify(timelineSupplier).apply(200);

        Context ctx3 = mock(Context.class);
        when(ctx3.queryParam("limit")).thenReturn("0");
        resource.get(ctx3);
        verify(timelineSupplier).apply(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void it_throws_bad_request_for_non_numeric_limit() {
        Supplier<List<IntegrationStatus>> statusSupplier = mock(Supplier.class);
        IntFunction<List<String>> timelineSupplier = mock(IntFunction.class);

        IntegrationResource resource = new IntegrationResource(statusSupplier, timelineSupplier);
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("abc");

        assertThrows(BadRequestResponse.class, () -> resource.get(ctx));
    }

    @Test
    @SuppressWarnings("unchecked")
    void it_returns_empty_arrays_when_no_integrations() {
        Supplier<List<IntegrationStatus>> statusSupplier = mock(Supplier.class);
        IntFunction<List<String>> timelineSupplier = mock(IntFunction.class);
        when(statusSupplier.get()).thenReturn(List.of());
        when(timelineSupplier.apply(anyInt())).thenReturn(List.of());

        IntegrationResource resource = new IntegrationResource(statusSupplier, timelineSupplier);
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn(null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.get(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<IntegrationsDto> envelope = (Envelope<IntegrationsDto>) captor.getValue();
        IntegrationsDto dto = envelope.data();
        assertEquals(0, dto.integrations.length);
        assertEquals(0, dto.timeline.length);
    }
}

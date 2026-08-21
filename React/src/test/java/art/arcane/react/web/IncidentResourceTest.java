package art.arcane.react.web;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IncidentDto;
import art.arcane.react.api.web.resource.IncidentResource;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IncidentResourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void it_returns_envelope_with_score_state_timeline_contributors() {
        DoubleSupplier scoreSupplier = () -> 58.0;
        Supplier<String> stateSupplier = () -> "ACTIVE";
        IntFunction<List<String>> timelineSupplier = l -> List.of("x entered", "x exited");
        Supplier<List<SamplerIncidentScore.Contribution>> contributorsSupplier =
            () -> List.of(new SamplerIncidentScore.Contribution("tick-ms-p95", 0.30, 120.0));

        IncidentResource resource = new IncidentResource(scoreSupplier, stateSupplier, timelineSupplier, contributorsSupplier);
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn(null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.get(ctx);
        verify(ctx).json(captor.capture());

        Envelope<IncidentDto> envelope = (Envelope<IncidentDto>) captor.getValue();
        IncidentDto dto = envelope.data();
        assertEquals(58.0, dto.score, 1e-9);
        assertEquals("ACTIVE", dto.state);
        assertEquals(2, dto.timeline.length);
        assertEquals(1, dto.contributors.length);
        assertEquals("tick-ms-p95", dto.contributors[0].name);
        assertEquals(0.30, dto.contributors[0].weight, 1e-9);
        assertEquals(120.0, dto.contributors[0].value, 1e-9);
    }

    @Test
    void it_throws_bad_request_for_non_numeric_limit() {
        IncidentResource resource = new IncidentResource(
            () -> 0.0,
            () -> "NORMAL",
            l -> List.of(),
            List::of
        );
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("abc");

        assertThrows(BadRequestResponse.class, () -> resource.get(ctx));
    }
}

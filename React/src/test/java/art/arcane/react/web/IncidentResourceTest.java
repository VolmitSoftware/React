package art.arcane.react.web;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.IncidentDto;
import art.arcane.react.api.web.resource.IncidentResource;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IncidentResourceTest {
  @Test
  @SuppressWarnings("unchecked")
  void returnsAtomicSnapshotAndStructuredIncidents() {
    IncidentEvidence evidence = new IncidentEvidence(
        "tick-ms-p95",
        "Tick P95",
        true,
        120D,
        "120 ms",
        0.7D,
        0.3D,
        21D,
        50D,
        150D
    );
    SamplerIncidentScore.IncidentScoreSnapshot snapshot = new SamplerIncidentScore.IncidentScoreSnapshot(
        1234L,
        58D,
        true,
        List.of(evidence)
    );
    IncidentRecord record = new IncidentRecord(
        "event-id",
        "incident-id",
        "SERVER_PRESSURE",
        "STARTED",
        "WARNING",
        1200L,
        1200L,
        "incident-mode",
        "Incident mode engaged",
        "Guardrails active",
        "Tick P95 was elevated",
        null,
        List.of(evidence),
        List.of(),
        Map.of()
    );
    IncidentResource resource = new IncidentResource(
        () -> snapshot,
        () -> "ACTIVE",
        limit -> List.of(record)
    );
    Context context = mock(Context.class);
    when(context.queryParam("limit")).thenReturn(null);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    resource.get(context);
    verify(context).json(captor.capture());

    Envelope<IncidentDto> envelope = (Envelope<IncidentDto>) captor.getValue();
    IncidentDto dto = envelope.data();
    assertEquals(58D, dto.score, 1.0E-9D);
    assertEquals(1234L, dto.sampledAtMs);
    assertEquals(true, dto.scoreAvailable);
    assertEquals("ACTIVE", dto.state);
    assertEquals(1, dto.incidents.length);
    assertEquals("Tick P95 was elevated", dto.incidents[0].cause());
    assertEquals(1, dto.contributors.length);
    assertEquals("tick-ms-p95", dto.contributors[0].id);
    assertEquals("120 ms", dto.contributors[0].display);
    assertEquals(21D, dto.contributors[0].scorePoints, 1.0E-9D);
  }

  @Test
  void throwsBadRequestForNonNumericLimit() {
    IncidentResource resource = new IncidentResource(
        SamplerIncidentScore.IncidentScoreSnapshot::empty,
        () -> "NORMAL",
        limit -> List.of()
    );
    Context context = mock(Context.class);
    when(context.queryParam("limit")).thenReturn("abc");

    assertThrows(BadRequestResponse.class, () -> resource.get(context));
  }
}

package art.arcane.react.web;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetricsResourceTest {

    private final List<String> injectedGraphKeys = new ArrayList<>();

    @AfterEach
    void cleanupGraphs() throws Exception {
        Field graphsField = Graph.class.getDeclaredField("graphs");
        graphsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Graph> graphsMap =
            (ConcurrentHashMap<String, Graph>) graphsField.get(null);
        for (String key : injectedGraphKeys) {
            graphsMap.remove(key);
        }
        injectedGraphKeys.clear();
    }

    @Test
    void snapshotReturnsEmptyArrayWhenRegistryIsNull() {
        SampleController controller = mock(SampleController.class);
        when(controller.getSamplers()).thenReturn(null);

        MetricsResource resource = new MetricsResource(controller, new MetricsSerializer());

        Context ctx = mock(Context.class);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.snapshot(ctx);
        verify(ctx).json(captor.capture());

        MetricsResource.SnapshotResponse response = (MetricsResource.SnapshotResponse) captor.getValue();
        assertEquals(0, response.data().length);
    }

    @Test
    void snapshotSerializesAllSamplers() throws Exception {
        SampleController controller = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);

        FakeSampler s1 = new FakeSampler("tick-time", 42.0, "ms", new double[]{40.0, 41.0, 42.0});
        FakeSampler s2 = new FakeSampler("tps", 20.0, "", new double[]{20.0, 20.0, 20.0});

        when(controller.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));

        MetricsSerializer serializer = new MetricsSerializer();
        MetricsResource resource = new MetricsResource(controller, serializer);

        Context ctx = mock(Context.class);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.snapshot(ctx);
        verify(ctx).json(captor.capture());

        MetricsResource.SnapshotResponse response = (MetricsResource.SnapshotResponse) captor.getValue();
        assertEquals(2, response.data().length);

        SamplerDto dto0 = response.data()[0];
        assertEquals("tick-time", dto0.id);
        assertEquals(42.0, dto0.value, 0.001);
        assertEquals("ms", dto0.suffix);
        assertEquals(3, dto0.history.length);

        SamplerDto dto1 = response.data()[1];
        assertEquals("tps", dto1.id);
        assertEquals(20.0, dto1.value, 0.001);
        assertEquals("", dto1.suffix);
        assertEquals(3, dto1.history.length);
    }

    @Test
    void it_snapshotData_returns_one_dto_per_sampler() {
        SampleController controller = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);

        FakeSampler s1 = new FakeSampler("cpu-load", 55.0, "%", new double[]{50.0, 55.0});
        FakeSampler s2 = new FakeSampler("mem-used", 2048.0, "MB", new double[]{2000.0, 2048.0});

        when(controller.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of(s1, s2));

        MetricsSerializer serializer = new MetricsSerializer();
        MetricsResource resource = new MetricsResource(controller, serializer);

        SamplerDto[] dtos = resource.snapshotData();
        assertEquals(2, dtos.length);
        assertEquals("cpu-load", dtos[0].id);
        assertEquals("mem-used", dtos[1].id);
    }

    @Test
    void historyReturns404ForUnknownId() {
        SampleController controller = mock(SampleController.class);
        when(controller.getSampler("nope")).thenReturn(null);

        MetricsResource resource = new MetricsResource(controller, new MetricsSerializer());

        Context ctx = mock(Context.class);
        when(ctx.pathParam("id")).thenReturn("nope");

        assertThrows(NotFoundResponse.class, () -> resource.history(ctx));
    }

    private final class FakeSampler implements Sampler {
        private final String id;
        private final double value;
        private final String suffix;

        FakeSampler(String id, double value, String suffix, double[] history) {
            this.id = id;
            this.value = value;
            this.suffix = suffix;
            try {
                Graph g = injectGraph(id, history);
                Field lastPushField = Graph.class.getDeclaredField("lastPushMs");
                lastPushField.setAccessible(true);
                lastPushField.set(g, System.currentTimeMillis());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private Graph injectGraph(String name, double[] history) throws Exception {
            Field graphsField = Graph.class.getDeclaredField("graphs");
            graphsField.setAccessible(true);
            ConcurrentHashMap<String, Graph> graphsMap =
                (ConcurrentHashMap<String, Graph>) graphsField.get(null);
            Graph g = new Graph();
            Field seqField = Graph.class.getDeclaredField("sequence");
            seqField.setAccessible(true);
            double[] seq = (double[]) seqField.get(g);
            for (int i = 0; i < history.length; i++) {
                seq[i] = history[i];
            }
            Field headField = Graph.class.getDeclaredField("head");
            headField.setAccessible(true);
            headField.set(g, history.length);
            Field sizeField = Graph.class.getDeclaredField("size");
            sizeField.setAccessible(true);
            sizeField.set(g, history.length);
            graphsMap.put(name, g);
            injectedGraphKeys.add(name);
            return g;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public double sample() {
            return value;
        }

        @Override
        public String formattedValue(double t) {
            return String.valueOf(t);
        }

        @Override
        public String formattedSuffix(double t) {
            return suffix;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void render() {}
    }
}

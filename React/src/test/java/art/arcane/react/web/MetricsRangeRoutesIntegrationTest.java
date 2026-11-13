package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.history.ChartHistory;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.project.registry.Registry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricsRangeRoutesIntegrationTest {
    private static final Map<String, Double> TYPICAL = Map.ofEntries(
        Map.entry("tick-time", 12.3456D),
        Map.entry("tick-ms-p50", 11.0731D),
        Map.entry("tick-ms-p95", 18.4412D),
        Map.entry("tick-ms-p99", 26.9087D),
        Map.entry("memory-used", 4_213_456_789D),
        Map.entry("memory-free", 3_871_234_567D),
        Map.entry("memory-used-after-gc", 2_104_567_890D),
        Map.entry("disk-read-rate", 152_345.67D),
        Map.entry("disk-write-rate", 2_412_345.6D),
        Map.entry("network-receive-rate", 85_432.1D),
        Map.entry("network-send-rate", 321_987.6D),
        Map.entry("react-async-tick-time", 0.35123D),
        Map.entry("react-sync-tick-time", 0.12345D)
    );

    private WebController controller;

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void rangeServesEveryChartedSamplerInOneCompactResponse(@TempDir File dataFolder) throws Exception {
        String bearer = WebTestTokens.issue(dataFolder, "range-device", "viewer");
        HistoryController history = new HistoryController();
        fillHour(history.getChartHistory(), System.currentTimeMillis());
        int port = startWeb(dataFolder, history, true);
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = get(client, port, "range=1h", bearer);

        assertEquals(200, response.statusCode(), response.body());
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals("1h", data.get("range").getAsString());
        assertEquals(10_000L, data.get("stepMs").getAsLong());
        long toMs = data.get("toMs").getAsLong();
        assertEquals(0L, toMs % 10_000L);
        assertEquals(toMs - 3_600_000L, data.get("fromMs").getAsLong());
        JsonArray series = data.getAsJsonArray("series");
        assertEquals(ChartHistory.SAMPLER_IDS.size(), series.size());
        for (int index = 0; index < series.size(); index++) {
            JsonObject entry = series.get(index).getAsJsonObject();
            assertEquals(ChartHistory.SAMPLER_IDS.get(index), entry.get("id").getAsString());
            JsonArray values = entry.getAsJsonArray("values");
            assertEquals(ChartRange.BUCKETS, values.size());
            for (JsonElement value : values) {
                assertFalse(value.isJsonNull(), "filled bucket serialized as null in " + entry.get("id"));
            }
        }
        assertTrue(response.body().length() < 64 * 1024, "range response is " + response.body().length() + " bytes");

        HttpResponse<String> selected = get(client, port, "range=24h&ids=tick-time,%20,unknown,memory-used", bearer);
        assertEquals(200, selected.statusCode(), selected.body());
        JsonObject selectedData = JsonParser.parseString(selected.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(240_000L, selectedData.get("stepMs").getAsLong());
        JsonArray selectedSeries = selectedData.getAsJsonArray("series");
        assertEquals(2, selectedSeries.size());
        assertEquals("tick-time", selectedSeries.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("memory-used", selectedSeries.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(360, selectedSeries.get(0).getAsJsonObject().getAsJsonArray("values").size());

        assertEquals(400, get(client, port, "range=7d", bearer).statusCode());
        assertEquals(400, get(client, port, "", bearer).statusCode());
        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add("id-" + index);
        }
        assertEquals(400, get(client, port, "range=1h&ids=" + String.join(",", tooMany), bearer).statusCode());
        assertEquals(400, get(client, port, "range=1h&ids=" + "x".repeat(257), bearer).statusCode());
        HttpResponse<String> rejected = get(client, port, "range=7d", bearer);
        JsonObject error = JsonParser.parseString(rejected.body()).getAsJsonObject().getAsJsonObject("error");
        assertEquals("bad_request", error.get("code").getAsString());

        assertEquals(401, get(client, port, "range=1h", null).statusCode());
    }

    @Test
    void rangeIsOpenWhenReadsNeedNoTokenAndUnavailableWithoutHistory(@TempDir File dataFolder) throws Exception {
        int port = startWeb(dataFolder, null, false);
        HttpClient client = HttpClient.newHttpClient();

        assertEquals(503, get(client, port, "range=1h", null).statusCode());
    }

    private int startWeb(File dataFolder, HistoryController history, boolean requireTokenForReads) throws Exception {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
        config.setPort(0);
        config.setRequireTokenForReads(requireTokenForReads);

        controller = new WebController() {
            @Override
            protected void executeAsync(Runnable r) {
                Thread.ofVirtual().start(r);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.serverName = "TestBrand";
                dto.serverId = "127.0.0.1:0";
                return dto;
            }
        };
        controller.setConfig(config);
        controller.setDataFolder(dataFolder);
        controller.setSampleController(sampleController);
        controller.setHistoryController(history);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);
        int port = controller.getBoundPort();
        assertTrue(port > 0, "Expected ephemeral port > 0 but got " + port);
        return port;
    }

    private static HttpResponse<String> get(HttpClient client, int port, String query, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/metrics/range" + (query.isEmpty() ? "" : "?" + query)))
            .GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void fillHour(ChartHistory history, long nowMs) {
        long step = ChartRange.HOUR.stepMs();
        for (int bucket = ChartRange.BUCKETS; bucket >= 0; bucket--) {
            long capturedAtMs = nowMs - bucket * step;
            double wave = 1D + 0.15D * Math.sin(bucket * 0.37D);
            List<MetricSnapshotValue> values = new ArrayList<>();
            for (String id : ChartHistory.SAMPLER_IDS) {
                double value = TYPICAL.get(id) * wave;
                values.add(new MetricSnapshotValue(id, id, "", value, "", true, capturedAtMs,
                    Sampler.Metadata.UNKNOWN, "", Sampler.Diagnostics.EMPTY));
            }
            history.record(MetricSnapshot.of(bucket, capturedAtMs, values));
        }
    }
}

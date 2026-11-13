package art.arcane.react.web;

import art.arcane.react.api.web.LogLine;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.LogWsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.core.controller.WebController;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class WebSocketScaleBoundTest {
    private static final int SESSION_COUNT = 1_000;
    private static final int LINES = 3_000;

    @Test
    void thousandStalledMetricAndLogSessionsStayBoundedAndRecoverInOrder() throws Exception {
        ExecutorService executorService = new ExposedWebController().createSendExecutor();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
        WebSocketSessions<CoalescingWsChannel> metricsSessions = new WebSocketSessions<>();
        WebSocketSessions<LogWsChannel> logSessions = new WebSocketSessions<>();
        List<RecordingTransport> metricTransports = new ArrayList<>(SESSION_COUNT);
        List<RecordingTransport> logTransports = new ArrayList<>(SESSION_COUNT);
        try {
            for (int index = 0; index < SESSION_COUNT; index++) {
                RecordingTransport metric = new RecordingTransport("metric-" + index);
                RecordingTransport log = new RecordingTransport("log-" + index);
                metric.hold();
                log.hold();
                metricTransports.add(metric);
                logTransports.add(log);
                metricsSessions.add(new CoalescingWsChannel(metric));
                logSessions.add(new LogWsChannel(log, executor, true, LogWsChannel.DEFAULT_CAPACITY));
            }

            long started = System.nanoTime();
            metricsSessions.forEachOpen(channel -> channel.send("metrics-first"));
            metricsSessions.forEachOpen(channel -> channel.send("metrics-middle"));
            metricsSessions.forEachOpen(channel -> channel.send("metrics-latest"));
            for (long seq = 1L; seq <= LINES; seq++) {
                LogLine line = new LogLine(seq, "log-" + seq);
                logSessions.forEachOpen(channel -> channel.offer(line));
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Assertions.assertTrue(elapsedMs < 10_000L, "stalled clients must not block producers; took " + elapsedMs + " ms");

            awaitIdle(executor);
            Assertions.assertTrue(executor.getPoolSize() <= 4);
            for (RecordingTransport transport : metricTransports) {
                Assertions.assertEquals(1, transport.heldWrites(), "one metrics write in flight per stalled client");
            }
            for (RecordingTransport transport : logTransports) {
                Assertions.assertEquals(1, transport.heldWrites(), "one log write in flight per stalled client");
            }
            logSessions.forEachOpen(channel -> Assertions.assertEquals(LogWsChannel.DEFAULT_CAPACITY, channel.queued()));

            metricTransports.forEach(RecordingTransport::release);
            logTransports.forEach(RecordingTransport::release);
            awaitIdle(executor);

            for (RecordingTransport transport : metricTransports) {
                Assertions.assertEquals(List.of("metrics-first", "metrics-latest"), transport.sent());
            }
            for (RecordingTransport transport : logTransports) {
                assertLogStream(transport.sent());
            }
        } finally {
            executorService.shutdownNow();
            Assertions.assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void assertLogStream(List<String> frames) {
        long expected = 1L;
        long dropped = 0L;
        for (String raw : frames) {
            JsonObject frame = JsonParser.parseString(raw).getAsJsonObject();
            if (frame.has("dropped")) {
                dropped += frame.get("dropped").getAsLong();
                expected += frame.get("dropped").getAsLong();
            }
            JsonArray lines = frame.getAsJsonArray("lines");
            for (int index = 0; index < lines.size(); index++) {
                Assertions.assertEquals(expected, lines.get(index).getAsJsonObject().get("seq").getAsLong());
                expected++;
            }
        }
        Assertions.assertEquals(LINES + 1L, expected, "every line is delivered or counted as dropped");
        long queuedBehindFirstFlush = LINES - LogWsChannel.DEFAULT_CAPACITY;
        Assertions.assertTrue(dropped <= queuedBehindFirstFlush - 1L && dropped >= queuedBehindFirstFlush - LogWsChannel.MAX_LINES_PER_FLUSH,
            "only lines beyond the bound are dropped; dropped " + dropped);
    }

    private static void awaitIdle(ThreadPoolExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (executor.getActiveCount() > 0 || !executor.getQueue().isEmpty()) {
            Assertions.assertTrue(System.nanoTime() < deadline, "send executor did not drain");
            Thread.sleep(10L);
        }
    }

    private static final class ExposedWebController extends WebController {
        private ExecutorService createSendExecutor() {
            return createWsSendExecutor();
        }
    }
}

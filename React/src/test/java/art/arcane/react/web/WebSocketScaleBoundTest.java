package art.arcane.react.web;

import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsChannel;
import art.arcane.react.core.controller.WebController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class WebSocketScaleBoundTest {
    private static final int SESSION_COUNT = 1_000;

    @Test
    void thousandMetricAndLogSessionsStayWithinFixedResourcesAndRecoverLatestFrames() throws Exception {
        ExposedWebController controller = new ExposedWebController();
        ExecutorService executorService = controller.createSendExecutor();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
        CountDownLatch release = new CountDownLatch(1);
        WebSocketSessions metricsSessions = new WebSocketSessions();
        WebSocketSessions logSessions = new WebSocketSessions();
        List<BlockingChannel> metricChannels = new ArrayList<>(SESSION_COUNT);
        List<BlockingChannel> logChannels = new ArrayList<>(SESSION_COUNT);

        try {
            for (int index = 0; index < SESSION_COUNT; index++) {
                BlockingChannel metricChannel = new BlockingChannel("metric-" + index, release);
                BlockingChannel logChannel = new BlockingChannel("log-" + index, release);
                metricChannels.add(metricChannel);
                logChannels.add(logChannel);
                metricsSessions.add(new CoalescingWsChannel(metricChannel, executor));
                logSessions.add(new CoalescingWsChannel(logChannel, executor));
            }

            metricsSessions.broadcast("metrics-first");
            metricsSessions.broadcast("metrics-latest");
            CoalescingWsChannel logBroadcaster = CoalescingWsChannel.broadcastTo(
                "log-broadcast",
                logSessions,
                executor
            );
            for (int line = 0; line < 10_000; line++) {
                logBroadcaster.send("log-" + line);
            }

            await(() -> executor.getPoolSize() == executor.getMaximumPoolSize(), 5_000L);
            Assertions.assertEquals(4, executor.getMaximumPoolSize());
            Assertions.assertTrue(executor.getPoolSize() <= 4);
            Assertions.assertTrue(executor.getQueue().size() <= 2_048);

            release.countDown();
            await(() -> allReceived(metricChannels, "metrics-latest"), 10_000L);
            await(() -> allReceived(logChannels, "log-9999"), 10_000L);
        } finally {
            release.countDown();
            executorService.shutdownNow();
            Assertions.assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean allReceived(List<BlockingChannel> channels, String frame) {
        for (BlockingChannel channel : channels) {
            if (!frame.equals(channel.lastFrame())) {
                return false;
            }
        }
        return true;
    }

    private void await(Condition condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.complete() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        Assertions.assertTrue(condition.complete(), "Condition did not complete within " + timeoutMs + " ms");
    }

    @FunctionalInterface
    private interface Condition {
        boolean complete();
    }

    private static final class ExposedWebController extends WebController {
        private ExecutorService createSendExecutor() {
            return createWsSendExecutor();
        }
    }

    private static final class BlockingChannel implements WsChannel {
        private final String channelId;
        private final CountDownLatch release;
        private volatile String lastFrame;

        private BlockingChannel(String channelId, CountDownLatch release) {
            this.channelId = channelId;
            this.release = release;
        }

        @Override
        public String id() {
            return channelId;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(String text) {
            try {
                release.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return;
            }
            lastFrame = text;
        }

        private String lastFrame() {
            return lastFrame;
        }
    }
}

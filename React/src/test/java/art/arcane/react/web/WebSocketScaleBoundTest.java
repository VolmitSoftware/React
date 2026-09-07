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
import java.util.concurrent.ThreadFactory;
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
        CountDownLatch poolFilled = new CountDownLatch(executor.getMaximumPoolSize());
        ThreadFactory poolFactory = executor.getThreadFactory();
        executor.setThreadFactory(runnable -> poolFactory.newThread(() -> {
            poolFilled.countDown();
            runnable.run();
        }));
        CountDownLatch metricsDelivered = new CountDownLatch(SESSION_COUNT);
        CountDownLatch logsDelivered = new CountDownLatch(SESSION_COUNT);
        WebSocketSessions metricsSessions = new WebSocketSessions();
        WebSocketSessions logSessions = new WebSocketSessions();
        List<BlockingChannel> metricChannels = new ArrayList<>(SESSION_COUNT);
        List<BlockingChannel> logChannels = new ArrayList<>(SESSION_COUNT);

        try {
            for (int index = 0; index < SESSION_COUNT; index++) {
                BlockingChannel metricChannel = new BlockingChannel(
                    "metric-" + index,
                    release,
                    "metrics-latest",
                    metricsDelivered
                );
                BlockingChannel logChannel = new BlockingChannel(
                    "log-" + index,
                    release,
                    "log-9999",
                    logsDelivered
                );
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

            Assertions.assertTrue(
                poolFilled.await(5, TimeUnit.SECONDS),
                "Send executor did not start its maximum pool size worth of threads within 5000 ms"
            );
            Assertions.assertEquals(4, executor.getMaximumPoolSize());
            Assertions.assertEquals(executor.getMaximumPoolSize(), executor.getPoolSize());
            Assertions.assertTrue(executor.getPoolSize() <= 4);
            Assertions.assertTrue(executor.getQueue().size() <= 2_048);

            release.countDown();
            Assertions.assertTrue(
                metricsDelivered.await(10, TimeUnit.SECONDS),
                "Metric sessions did not all receive metrics-latest within 10000 ms"
            );
            Assertions.assertTrue(
                logsDelivered.await(10, TimeUnit.SECONDS),
                "Log sessions did not all receive log-9999 within 10000 ms"
            );
            Assertions.assertTrue(allReceived(metricChannels, "metrics-latest"));
            Assertions.assertTrue(allReceived(logChannels, "log-9999"));
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

    private static final class ExposedWebController extends WebController {
        private ExecutorService createSendExecutor() {
            return createWsSendExecutor();
        }
    }

    private static final class BlockingChannel implements WsChannel {
        private final String channelId;
        private final CountDownLatch release;
        private final String target;
        private final CountDownLatch delivered;
        private volatile String lastFrame;

        private BlockingChannel(String channelId, CountDownLatch release, String target, CountDownLatch delivered) {
            this.channelId = channelId;
            this.release = release;
            this.target = target;
            this.delivered = delivered;
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
            if (target.equals(text)) {
                delivered.countDown();
            }
        }

        private String lastFrame() {
            return lastFrame;
        }
    }
}

package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.RingLogHandler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

class WebControllerLifecycleTest {
    private WebController controller;

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void stopDuringStartupPreventsPublicationAndClosesLocalResources(@TempDir File dataFolder) throws Exception {
        int port = reservePort();
        Logger logger = Logger.getLogger("react-web-stop-during-start-" + System.nanoTime());
        BlockingWebController blockingController = new BlockingWebController(logger);
        controller = blockingController;
        configure(blockingController, dataFolder, port);

        blockingController.start();
        blockingController.postStart();
        Assertions.assertTrue(blockingController.awaitPushExecutorCreation(5, TimeUnit.SECONDS));

        blockingController.stop();
        Assertions.assertThrows(RuntimeException.class, () -> blockingController.awaitStart(1000L));
        blockingController.releasePushExecutorCreation();
        Assertions.assertTrue(blockingController.awaitAsyncCompletion(5, TimeUnit.SECONDS));

        assertStopped(blockingController);
        Assertions.assertEquals(0, logger.getHandlers().length);
        assertPortAvailable(port);
    }

    @Test
    void stoppedStartupCannotBindAfterReplacementStarts(@TempDir File dataFolder) throws Exception {
        int port = reservePort();
        Logger logger = Logger.getLogger("react-web-bind-generation-" + System.nanoTime());
        BindBlockingWebController bindBlockingController = new BindBlockingWebController(logger);
        controller = bindBlockingController;
        configure(bindBlockingController, dataFolder, port);

        bindBlockingController.start();
        bindBlockingController.postStart();
        Assertions.assertTrue(bindBlockingController.awaitFirstBind(5, TimeUnit.SECONDS));

        bindBlockingController.stop();
        bindBlockingController.start();
        bindBlockingController.postStart();
        bindBlockingController.awaitStart(5000L);
        bindBlockingController.releaseFirstBind();
        Assertions.assertTrue(bindBlockingController.awaitAsyncCompletions(5, TimeUnit.SECONDS));

        Assertions.assertEquals(port, bindBlockingController.getBoundPort());
        Assertions.assertNull(bindBlockingController.getStartFailure());
        assertPing(port);

        bindBlockingController.stop();
        assertStopped(bindBlockingController);
        assertPortAvailable(port);
    }

    @Test
    void completedRuntimeCanStopAndRestartWithFreshResources(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-restart-" + System.nanoTime());
        AsyncWebController asyncController = new AsyncWebController(logger);
        controller = asyncController;
        configure(asyncController, dataFolder, 0);

        asyncController.start();
        asyncController.postStart();
        asyncController.awaitStart(5000L);

        Javalin firstApp = asyncController.getApp();
        ExecutorService firstSendExecutor = asyncController.getWsSendExecutor();
        ScheduledExecutorService firstPushExecutor = asyncController.getWsPushExecutor();
        RingLogHandler firstLogHandler = asyncController.getLogHandler();
        int firstPort = asyncController.getBoundPort();
        assertRunning(asyncController);
        assertPing(firstPort);

        asyncController.stop();
        assertStopped(asyncController);
        Assertions.assertTrue(asyncController.pairingUnavailableReason().contains("not bound"));
        Assertions.assertThrows(IllegalStateException.class, asyncController::resolveDirectUrl);
        Assertions.assertTrue(firstSendExecutor.isShutdown());
        Assertions.assertTrue(firstPushExecutor.isShutdown());
        Assertions.assertFalse(List.of(logger.getHandlers()).contains(firstLogHandler));

        asyncController.start();
        asyncController.postStart();
        asyncController.awaitStart(5000L);

        assertRunning(asyncController);
        Assertions.assertNotSame(firstApp, asyncController.getApp());
        Assertions.assertNotSame(firstSendExecutor, asyncController.getWsSendExecutor());
        Assertions.assertNotSame(firstPushExecutor, asyncController.getWsPushExecutor());
        Assertions.assertNotSame(firstLogHandler, asyncController.getLogHandler());
        assertPing(asyncController.getBoundPort());
    }

    @Test
    void occupiedConfiguredPortFallsForwardToTheNextAvailablePort(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-bind-fallback-" + System.nanoTime());
        AsyncWebController asyncController = new AsyncWebController(logger);
        controller = asyncController;

        try (PortReservation occupied = reserveConsecutivePorts(3)) {
            int requestedPort = occupied.firstPort();
            configure(asyncController, dataFolder, requestedPort);

            asyncController.start();
            asyncController.postStart();
            asyncController.awaitStart(5000L);

            Assertions.assertEquals(requestedPort + 3, asyncController.getBoundPort());
            Assertions.assertNull(asyncController.getStartFailure());
            Assertions.assertNull(asyncController.pairingUnavailableReason());
            Assertions.assertEquals(
                "http://127.0.0.1:" + (requestedPort + 3),
                asyncController.resolveDirectUrl()
            );
            assertPing(asyncController.getBoundPort());
            Assertions.assertTrue(asyncController.getTokenStore().all().isEmpty());
            Assertions.assertFalse(asyncController.tokensFile().exists());
        }
    }

    @Test
    void exhaustedPortSearchUsesConciseBindFailurePath(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-bind-exhaustion-" + System.nanoTime());
        LimitedPortWebController limitedController = new LimitedPortWebController(logger, 1);
        controller = limitedController;

        try (PortReservation occupied = reserveConsecutivePorts(1)) {
            int requestedPort = occupied.firstPort();
            configure(limitedController, dataFolder, requestedPort);

            limitedController.start();
            limitedController.postStart();
            Assertions.assertThrows(RuntimeException.class, () -> limitedController.awaitStart(5000L));

            Assertions.assertNull(limitedController.getApp());
            Assertions.assertEquals(0, limitedController.getBoundPort());
            Assertions.assertNotNull(limitedController.getStartFailure());
            Assertions.assertTrue(limitedController.pairingUnavailableReason().contains("failed to start"));
            Assertions.assertEquals(
                "React web listener could not bind to port range " + requestedPort
                    + "; listener remains disabled.",
                limitedController.getBindFailureMessage()
            );
        }
    }

    @Test
    void startupUsesPluginClassLoaderAndRestoresWorkerContext(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-context-loader-" + System.nanoTime());
        ClassLoader marker = new ClassLoader(null) {
        };
        ContextClassLoaderWebController contextController = new ContextClassLoaderWebController(logger, marker);
        controller = contextController;
        configure(contextController, dataFolder, 0);

        contextController.start();
        contextController.postStart();
        contextController.awaitStart(5000L);
        Assertions.assertTrue(contextController.awaitAsyncCompletion(5, TimeUnit.SECONDS));

        Assertions.assertSame(WebController.class.getClassLoader(), contextController.getStartupContextClassLoader());
        Assertions.assertSame(marker, contextController.getRestoredContextClassLoader());
    }

    private void configure(WebController target, File dataFolder, int port) {
        WebConfiguration configuration = new WebConfiguration();
        configuration.setListenerEnabled(true);
        configuration.setListenAddress("127.0.0.1");
        configuration.setPort(port);
        configuration.setRelayEnabled(false);
        target.setConfig(configuration);
        target.setDataFolder(dataFolder);
        target.setSampleController(emptySampleController());
    }

    @SuppressWarnings("unchecked")
    private SampleController emptySampleController() {
        SampleController sampleController = Mockito.mock(SampleController.class);
        Registry<Sampler> registry = Mockito.mock(Registry.class);
        Mockito.when(sampleController.getSamplers()).thenReturn(registry);
        Mockito.when(registry.all()).thenReturn(List.of());
        return sampleController;
    }

    private void assertRunning(WebController target) {
        Assertions.assertNotNull(target.getApp());
        Assertions.assertTrue(target.getBoundPort() > 0);
        Assertions.assertNotNull(target.getMetricsSessions());
        Assertions.assertNotNull(target.getMetricsBroadcaster());
        Assertions.assertNotNull(target.getWsPushExecutor());
        Assertions.assertNotNull(target.getWsSendExecutor());
        Assertions.assertNotNull(target.getLogHandler());
        Assertions.assertNotNull(target.getLogSessions());
        Assertions.assertNotNull(target.getWsAuthenticationGate());
        Assertions.assertNotNull(target.getAttachedLogger());
        Assertions.assertNull(target.getStartFailure());
    }

    private void assertStopped(WebController target) {
        Assertions.assertNull(target.getApp());
        Assertions.assertEquals(0, target.getBoundPort());
        Assertions.assertNull(target.getRelayClient());
        Assertions.assertNull(target.getMetricsSessions());
        Assertions.assertNull(target.getMetricsBroadcaster());
        Assertions.assertNull(target.getWsPushExecutor());
        Assertions.assertNull(target.getWsSendExecutor());
        Assertions.assertNull(target.getLogHandler());
        Assertions.assertNull(target.getLogSessions());
        Assertions.assertNull(target.getWsAuthenticationGate());
        Assertions.assertNull(target.getAttachedLogger());
        Assertions.assertNull(target.getLog4jConsoleCapture());
    }

    private void assertPing(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/ping"))
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        Assertions.assertEquals(200, response.statusCode());
    }

    private int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private PortReservation reserveConsecutivePorts(int occupiedCount) throws IOException {
        for (int candidatePort = 20000; candidatePort < 65000 - occupiedCount; candidatePort++) {
            List<ServerSocket> occupied = new ArrayList<>(occupiedCount);
            try {
                for (int offset = 0; offset < occupiedCount; offset++) {
                    ServerSocket socket = new ServerSocket();
                    socket.bind(new InetSocketAddress("127.0.0.1", candidatePort + offset));
                    occupied.add(socket);
                }
                try (ServerSocket successor = new ServerSocket()) {
                    successor.bind(new InetSocketAddress("127.0.0.1", candidatePort + occupiedCount));
                }
                return new PortReservation(candidatePort, occupied);
            } catch (IOException failure) {
                closeSockets(occupied);
            }
        }
        throw new IOException("Could not reserve consecutive occupied ports with an available successor");
    }

    private static void closeSockets(List<ServerSocket> sockets) throws IOException {
        IOException closeFailure = null;
        for (ServerSocket socket : sockets) {
            try {
                socket.close();
            } catch (IOException failure) {
                if (closeFailure == null) {
                    closeFailure = failure;
                } else {
                    closeFailure.addSuppressed(failure);
                }
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private void assertPortAvailable(int port) throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }

    private static class AsyncWebController extends WebController {
        private final Logger logger;

        private AsyncWebController(Logger logger) {
            this.logger = logger;
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(runnable);
        }

        @Override
        protected IdentityDto resolveIdentity() {
            IdentityDto identity = new IdentityDto();
            identity.version = "lifecycle-test";
            identity.serverName = "LifecycleTest";
            identity.folia = false;
            identity.serverId = "127.0.0.1:0";
            return identity;
        }

        @Override
        protected Logger resolveConsoleLogger() {
            return logger;
        }
    }

    private static final class LimitedPortWebController extends AsyncWebController {
        private final int attempts;
        private final AtomicReference<String> bindFailureMessage = new AtomicReference<>();

        private LimitedPortWebController(Logger logger, int attempts) {
            super(logger);
            this.attempts = attempts;
        }

        @Override
        protected int portSearchAttempts() {
            return attempts;
        }

        @Override
        protected void reportPortBindFailure(int requestedPort, int lastPort) {
            String ports = requestedPort == lastPort
                ? Integer.toString(requestedPort)
                : requestedPort + "-" + lastPort;
            bindFailureMessage.set(
                "React web listener could not bind to port range " + ports + "; listener remains disabled."
            );
        }

        private String getBindFailureMessage() {
            return bindFailureMessage.get();
        }
    }

    private static final class BlockingWebController extends AsyncWebController {
        private final CountDownLatch pushExecutorCreation = new CountDownLatch(1);
        private final CountDownLatch releasePushExecutorCreation = new CountDownLatch(1);
        private final CountDownLatch asyncCompletion = new CountDownLatch(1);

        private BlockingWebController(Logger logger) {
            super(logger);
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                try {
                    runnable.run();
                } finally {
                    asyncCompletion.countDown();
                }
            });
        }

        @Override
        protected ScheduledExecutorService createWsPushExecutor() {
            pushExecutorCreation.countDown();
            try {
                if (!releasePushExecutorCreation.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release WebController startup");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release WebController startup", failure);
            }
            return super.createWsPushExecutor();
        }

        private boolean awaitPushExecutorCreation(long timeout, TimeUnit unit) throws InterruptedException {
            return pushExecutorCreation.await(timeout, unit);
        }

        private void releasePushExecutorCreation() {
            releasePushExecutorCreation.countDown();
        }

        private boolean awaitAsyncCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return asyncCompletion.await(timeout, unit);
        }
    }

    private static final class BindBlockingWebController extends AsyncWebController {
        private final AtomicBoolean blockFirstBind = new AtomicBoolean(true);
        private final CountDownLatch firstBind = new CountDownLatch(1);
        private final CountDownLatch releaseFirstBind = new CountDownLatch(1);
        private final CountDownLatch asyncCompletions = new CountDownLatch(2);

        private BindBlockingWebController(Logger logger) {
            super(logger);
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                try {
                    runnable.run();
                } finally {
                    asyncCompletions.countDown();
                }
            });
        }

        @Override
        protected void beforeListenerBind() {
            if (!blockFirstBind.compareAndSet(true, false)) {
                return;
            }
            firstBind.countDown();
            try {
                if (!releaseFirstBind.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release WebController listener bind");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release WebController listener bind", failure);
            }
        }

        private boolean awaitFirstBind(long timeout, TimeUnit unit) throws InterruptedException {
            return firstBind.await(timeout, unit);
        }

        private void releaseFirstBind() {
            releaseFirstBind.countDown();
        }

        private boolean awaitAsyncCompletions(long timeout, TimeUnit unit) throws InterruptedException {
            return asyncCompletions.await(timeout, unit);
        }
    }

    private static final class ContextClassLoaderWebController extends AsyncWebController {
        private final ClassLoader marker;
        private final AtomicReference<ClassLoader> startupContextClassLoader = new AtomicReference<>();
        private final AtomicReference<ClassLoader> restoredContextClassLoader = new AtomicReference<>();
        private final CountDownLatch asyncCompletion = new CountDownLatch(1);

        private ContextClassLoaderWebController(Logger logger, ClassLoader marker) {
            super(logger);
            this.marker = marker;
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                Thread thread = Thread.currentThread();
                thread.setContextClassLoader(marker);
                try {
                    runnable.run();
                } finally {
                    restoredContextClassLoader.set(thread.getContextClassLoader());
                    asyncCompletion.countDown();
                }
            });
        }

        @Override
        protected ScheduledExecutorService createWsPushExecutor() {
            startupContextClassLoader.set(Thread.currentThread().getContextClassLoader());
            return super.createWsPushExecutor();
        }

        private boolean awaitAsyncCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return asyncCompletion.await(timeout, unit);
        }

        private ClassLoader getStartupContextClassLoader() {
            return startupContextClassLoader.get();
        }

        private ClassLoader getRestoredContextClassLoader() {
            return restoredContextClassLoader.get();
        }
    }

    private record PortReservation(int firstPort, List<ServerSocket> sockets) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            closeSockets(sockets);
        }
    }
}

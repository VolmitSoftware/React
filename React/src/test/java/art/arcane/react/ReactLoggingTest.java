package art.arcane.react;

import art.arcane.react.model.ReactConfiguration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class ReactLoggingTest {
  private React previousInstance;
  private ReactConfiguration previousConfiguration;
  private Logger logger;
  private RecordingHandler handler;
  private ReactConfiguration configuration;
  private MockedStatic<ComponentLogger> componentLoggerFactory;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    previousInstance = React.instance;
    previousConfiguration = readConfiguration();
    configuration = new ReactConfiguration();
    ReactConfiguration.applyHotloadSnapshot(configuration);

    logger = Logger.getLogger("ReactLoggingTest-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    logger.addHandler(handler);

    componentLoggerFactory = Mockito.mockStatic(ComponentLogger.class);
    componentLoggerFactory.when(ComponentLogger::logger).thenReturn(null);

    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getLogger()).thenReturn(logger);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() throws ReflectiveOperationException {
    componentLoggerFactory.close();
    logger.removeHandler(handler);
    React.instance = previousInstance;
    writeConfiguration(previousConfiguration);
  }

  @Test
  void normalMessagesPreserveJulSeverity() {
    React.info("information");
    React.warn("warning");
    React.error("failure");

    List<LogRecord> records = handler.records();
    Assertions.assertEquals(3, records.size());
    Assertions.assertEquals(Level.INFO, records.get(0).getLevel());
    Assertions.assertEquals(Level.WARNING, records.get(1).getLevel());
    Assertions.assertEquals(Level.SEVERE, records.get(2).getLevel());
    Assertions.assertTrue(records.get(0).getMessage().contains("information"));
    Assertions.assertTrue(records.get(1).getMessage().contains("warning"));
    Assertions.assertTrue(records.get(2).getMessage().contains("failure"));
    Assertions.assertTrue(records.stream().noneMatch(record -> record.getMessage().contains("\u00a7")));
  }

  @Test
  void reportErrorRetainsContextCauseAndAccounting() {
    int before = React.reportedErrorCount();
    IllegalStateException failure = new IllegalStateException("broken");

    React.reportError("Contextual failure", failure);

    Assertions.assertEquals(before + 1, React.reportedErrorCount());
    LogRecord record = handler.records().getFirst();
    Assertions.assertEquals(Level.SEVERE, record.getLevel());
    Assertions.assertTrue(record.getMessage().contains("Contextual failure"));
    Assertions.assertSame(failure, record.getThrown());
  }

  @Test
  void debugAndVerboseMessagesRequireTheirConfigurationFlags() {
    AtomicInteger supplierEvaluations = new AtomicInteger();
    React.debug("hidden debug");
    React.verbose("hidden verbose");
    React.debug(() -> {
      supplierEvaluations.incrementAndGet();
      return "hidden lazy debug";
    });
    React.verbose(() -> {
      supplierEvaluations.incrementAndGet();
      return "hidden lazy verbose";
    });
    Assertions.assertTrue(handler.records().isEmpty());
    Assertions.assertEquals(0, supplierEvaluations.get());

    configuration.setDebug(true);
    configuration.setVerbose(true);
    React.debug(() -> {
      supplierEvaluations.incrementAndGet();
      return "visible debug";
    });
    React.verbose(() -> {
      supplierEvaluations.incrementAndGet();
      return "visible verbose";
    });

    List<LogRecord> records = handler.records();
    Assertions.assertEquals(2, records.size());
    Assertions.assertEquals(2, supplierEvaluations.get());
    Assertions.assertEquals(Level.INFO, records.get(0).getLevel());
    Assertions.assertEquals(Level.INFO, records.get(1).getLevel());
    Assertions.assertTrue(records.get(0).getMessage().contains("visible debug"));
    Assertions.assertTrue(records.get(1).getMessage().contains("visible verbose"));
  }

  private static ReactConfiguration readConfiguration() throws ReflectiveOperationException {
    Field field = ReactConfiguration.class.getDeclaredField("configuration");
    field.setAccessible(true);
    return (ReactConfiguration) field.get(null);
  }

  private static void writeConfiguration(ReactConfiguration value) throws ReflectiveOperationException {
    Field field = ReactConfiguration.class.getDeclaredField("configuration");
    field.setAccessible(true);
    field.set(null, value);
  }

  private static final class RecordingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (record != null && isLoggable(record)) {
        records.add(record);
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private List<LogRecord> records() {
      return List.copyOf(records);
    }
  }
}

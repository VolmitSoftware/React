package art.arcane.react.api.web.ws;

import java.util.function.Consumer;

public interface WsTransport {
  String id();

  boolean isOpen();

  /** Never blocks; {@code completion} receives null on success or the write failure. */
  void sendAsync(String text, Consumer<Throwable> completion);

  void ping();

  void close(int code, String reason);
}

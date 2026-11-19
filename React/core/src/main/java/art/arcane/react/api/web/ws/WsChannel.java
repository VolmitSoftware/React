package art.arcane.react.api.web.ws;

public interface WsChannel {
  String id();

  boolean isOpen();

  void ping();

  void close(int code, String reason);
}

package art.arcane.react.api.web.ws;

import java.util.concurrent.ConcurrentHashMap;

public final class WebSocketSessions {

  private final ConcurrentHashMap<String, WsChannel> channels = new ConcurrentHashMap<>();

  public void add(WsChannel channel) {
    channels.put(channel.id(), channel);
  }

  public void remove(String id) {
    channels.remove(id);
  }

  public int size() {
    return channels.size();
  }

  public boolean isEmpty() {
    return channels.isEmpty();
  }

  public int broadcast(String text) {
    int count = 0;
    for (WsChannel channel : channels.values()) {
      if (!channel.isOpen()) {
        remove(channel.id());
        continue;
      }
      try {
        channel.send(text);
        count++;
      } catch (Throwable t) {
        remove(channel.id());
      }
    }
    return count;
  }
}

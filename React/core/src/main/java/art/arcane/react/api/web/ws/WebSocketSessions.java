package art.arcane.react.api.web.ws;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WebSocketSessions<C extends WsChannel> {

  private final ConcurrentHashMap<String, Entry<C>> channels = new ConcurrentHashMap<>();

  public void add(C channel) {
    add(channel, null);
  }

  public void add(C channel, String tokenId) {
    channels.put(channel.id(), new Entry<>(channel, tokenId));
  }

  public void bindToken(String id, String tokenId) {
    channels.computeIfPresent(id, (key, entry) -> new Entry<>(entry.channel(), tokenId));
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

  /** Runs {@code action} on every open channel and prunes closed ones. */
  public int forEachOpen(Consumer<? super C> action) {
    int count = 0;
    for (Entry<C> entry : channels.values()) {
      C channel = entry.channel();
      if (!channel.isOpen()) {
        channels.remove(channel.id(), entry);
        continue;
      }
      try {
        action.accept(channel);
        count++;
      } catch (Throwable failure) {
        channels.remove(channel.id(), entry);
      }
    }
    return count;
  }

  /** The open channel with this session id, or null. */
  public C get(String id) {
    Entry<C> entry = channels.get(id);
    return entry == null ? null : entry.channel();
  }

  /** How many channels authenticated with {@code tokenId}. */
  public int count(String tokenId) {
    int count = 0;
    for (Entry<C> entry : channels.values()) {
      if (tokenId.equals(entry.tokenId())) {
        count++;
      }
    }
    return count;
  }

  /** The token ids of every authenticated channel. */
  public Set<String> tokenIds() {
    Set<String> ids = new HashSet<>();
    for (Entry<C> entry : channels.values()) {
      if (entry.tokenId() != null) {
        ids.add(entry.tokenId());
      }
    }
    return ids;
  }

  /** Closes and removes every channel authenticated with {@code tokenId}. */
  public int closeToken(String tokenId, int code, String reason) {
    if (tokenId == null) {
      return 0;
    }
    int closed = 0;
    for (Entry<C> entry : channels.values()) {
      if (!tokenId.equals(entry.tokenId())) {
        continue;
      }
      if (channels.remove(entry.channel().id(), entry)) {
        entry.channel().close(code, reason);
        closed++;
      }
    }
    return closed;
  }

  private record Entry<C>(C channel, String tokenId) {
  }
}

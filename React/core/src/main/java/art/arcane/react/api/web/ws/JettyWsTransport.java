package art.arcane.react.api.web.ws;

import io.javalin.websocket.WsContext;
import org.eclipse.jetty.websocket.api.WriteCallback;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class JettyWsTransport implements WsTransport {
  private final WsContext ctx;

  public JettyWsTransport(WsContext ctx) {
    this.ctx = ctx;
  }

  @Override
  public String id() {
    return ctx.sessionId();
  }

  @Override
  public boolean isOpen() {
    return ctx.session.isOpen();
  }

  @Override
  public void sendAsync(String text, Consumer<Throwable> completion) {
    try {
      ctx.session.getRemote().sendString(text, new WriteCallback() {
        @Override
        public void writeFailed(Throwable failure) {
          completion.accept(failure);
        }

        @Override
        public void writeSuccess() {
          completion.accept(null);
        }
      });
    } catch (Throwable failure) {
      completion.accept(failure);
    }
  }

  @Override
  public void ping() {
    try {
      ctx.session.getRemote().sendPing(ByteBuffer.allocate(0), WriteCallback.NOOP);
    } catch (Throwable ignored) {
      // a failed ping means the session is closing; its close handler prunes it
    }
  }

  @Override
  public void close(int code, String reason) {
    try {
      ctx.closeSession(code, reason);
    } catch (Throwable ignored) {
      // already closed
    }
  }
}

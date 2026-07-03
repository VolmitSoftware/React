package art.arcane.react.api.web.ws;

import io.javalin.websocket.WsContext;

public final class JavalinWsChannel implements WsChannel {

    private final WsContext ctx;

    public JavalinWsChannel(WsContext ctx) {
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
    public void send(String text) {
        ctx.send(text);
    }
}

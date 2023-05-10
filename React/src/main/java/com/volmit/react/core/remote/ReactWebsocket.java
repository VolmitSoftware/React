package com.volmit.react.core.remote;

import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.content.feature.FeatureRemote;
import com.volmit.react.core.remote.message.ReactMessage;
import com.volmit.react.model.ReactRemoteUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReactWebsocket extends WebSocketAdapter {
    private static final Gson gson = new Gson();
    private ReactRemoteUser user;
    private String username;
    private FeatureRemote feature;

    public boolean canConsole() {
        return user != null && user.isConsole();
    }

    public boolean canFiles() {
        return user != null && user.isFiles();
    }

    public boolean canActions() {
        return user != null && user.isActions();
    }

    public boolean canSample() {
        return user != null && user.isSample();
    }

    public void send(ReactMessage message) {
        try {
            getRemote().sendString(gson.toJson(message));
        }

        catch(Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        feature = (FeatureRemote) React.instance.getFeatureController().getFeature(FeatureRemote.ID);
    }

    @Override
    public void onWebSocketText(String message) {
        super.onWebSocketText(message);
        ReactMessage rm = gson.fromJson(message, ReactMessage.class);
        String id = rm.getType();
        Class<?> type = feature.getClientMessages().get(id);

        if(type != null) {
            ReactMessage m = (ReactMessage) gson.fromJson(message, type);
            m.handle(this);
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);

        if(username != null) {
            getFeature().getConnections().remove(username);
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        cause.printStackTrace(System.err);
    }
}

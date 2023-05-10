package com.volmit.react.content.feature;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.RemoteServer;
import com.volmit.react.core.remote.message.ReactMessage;
import com.volmit.react.core.remote.message.server.RSActions;
import com.volmit.react.core.remote.message.server.RSSampleData;
import com.volmit.react.core.remote.message.server.RSSamplers;
import com.volmit.react.model.ReactRemoteUser;
import com.volmit.react.util.scheduling.J;
import lombok.Getter;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class FeatureRemote extends ReactFeature {
    public static final String ID = "remote";
    private transient final Map<String, Class<?>> serverMessages;
    private transient final Map<String, Class<?>> clientMessages;
    private transient Map<String, ReactWebsocket> connections;
    private transient RemoteServer server;
    private Map<String, ReactRemoteUser> users = defaultUserMap();
    private int port = 8828;

    public FeatureRemote() {
        super(ID);
        setEnabled(false); // default off
        serverMessages = new HashMap<>();
        clientMessages = new HashMap<>();
        Curse.whereInPackage(getClass(), (c) -> c.isAssignableFrom(ReactMessage.class) || ReactMessage.class.isAssignableFrom(c),
            "com.volmit.react.core.remote.message.server").forEach((c) -> {
            ReactMessage rm = c.construct().instance();
            serverMessages.put(rm.getType(), c.type());
        });
        Curse.whereInPackage(getClass(), (c) -> c.isAssignableFrom(ReactMessage.class) || ReactMessage.class.isAssignableFrom(c),
            "com.volmit.react.core.remote.message.client").forEach((c) -> {
            ReactMessage rm = c.construct().instance();
            clientMessages.put(rm.getType(), c.type());
        });
    }

    @Override
    public void onActivate() {
        connections = new HashMap<>();
        try {
            server = new RemoteServer(port);
        } catch(Exception e) {
            e.printStackTrace();
            React.error("Failed to start the remote server!");
        }
    }

    @Override
    public void onDeactivate() {
        if(server != null) {
            server.stop();
        }
    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    @Override
    public void onTick() {
        RSSampleData data = null;
        for(ReactWebsocket i : getConnections().values()) {
            if(i.isNotConnected()) {
                continue;
            }

            if(i.canSample()) {
                if(data == null) {
                    data = new RSSampleData();

                    for(Sampler j : React.instance.getSampleController().getSamplers().values()) {
                        data.sample(j);
                    }
                }

                i.send(data);
            }
        }
    }

    private static Map<String, ReactRemoteUser> defaultUserMap() {
        Map<String, ReactRemoteUser> map = new HashMap<>();
        map.put("cyberpwn", ReactRemoteUser.builder()
                .password("rand" + UUID.randomUUID().toString().replace("-", ""))
            .build());

        return map;
    }

    public void join(String user, ReactWebsocket socket) {
        getConnections().put(user, socket);

        J.a(() -> {
            if(socket.canSample()) {
                socket.send(new RSSamplers()
                    .setSamplers(new ArrayList<>(React.instance.getSampleController().getSamplers().keySet())));
            }

            if(socket.canActions()) {
                socket.send(new RSActions()
                    .setActions(new ArrayList<>(React.instance.getActionController().getActions().keySet())));
            }
        });
    }
}

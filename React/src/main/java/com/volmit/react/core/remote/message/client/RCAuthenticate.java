package com.volmit.react.core.remote.message.client;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import com.volmit.react.model.ReactRemoteUser;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.scheduling.J;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Builder
@Data
public class RCAuthenticate extends ReactMessage {
    private String user;
    private String password;

    public RCAuthenticate() {
        super("authenticate");
    }

    @Override
    public void handle(ReactWebsocket socket) {
        if(socket.getUser() != null) {
            return;
        }

        ReactRemoteUser u = socket.getFeature().getUsers().get(user);
        PrecisionStopwatch p = PrecisionStopwatch.start();

        if(IO.hash(u.getPassword()).equalsIgnoreCase(password)) {
            socket.setUser(u);
            socket.setUsername(user);
            socket.getFeature().join(user, socket);
        }

        else {
            socket.setUser(null);
            socket.setUsername(null);
        }

        J.sleep(Math.max(0, 3000 - (int)p.getMilliseconds())); // timing attack
    }
}

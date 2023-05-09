package com.volmit.react.core.remote.message.client;

import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ROK extends ReactMessage {
    private boolean ok = true;
    private String error = null;

    public ROK() {
        super("ok");
    }

    @Override
    public void handle(ReactWebsocket socket) {

    }
}

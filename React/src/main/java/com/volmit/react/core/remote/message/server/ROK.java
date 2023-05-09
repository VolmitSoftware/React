package com.volmit.react.core.remote.message.server;

import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Builder
@Data
public class ROK extends ReactMessage {
    @Builder.Default
    private boolean ok = true;
    @Builder.Default
    private String error = null;

    public ROK() {
        super("ok");
    }

    @Override
    public void handle(ReactWebsocket socket) {

    }
}

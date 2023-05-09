package com.volmit.react.core.remote.message.server;

import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(
    chain = true
)
public class RSActions extends ReactMessage {
    private List<String> actions;

    public RSActions() {
        super("actions");
    }

    @Override
    public void handle(ReactWebsocket socket) {

    }
}

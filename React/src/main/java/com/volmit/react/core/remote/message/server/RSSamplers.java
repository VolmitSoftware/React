package com.volmit.react.core.remote.message.server;

import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(
    chain = true
)
public class RSSamplers extends ReactMessage {
    private List<String> samplers;

    public RSSamplers() {
        super("samplers");
    }

    @Override
    public void handle(ReactWebsocket socket) {

    }
}

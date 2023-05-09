package com.volmit.react.core.remote.message;

import com.volmit.react.core.remote.ReactWebsocket;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Random;

@Data
public abstract class ReactMessage {
    private static final Random random = new Random();
    private String type;
    private int id;

    public ReactMessage(String type) {
        this.type = type;
        id = random.nextInt();
    }

    public ReactMessage() {
        this("unknown");
    }

    public abstract void handle(ReactWebsocket socket);
}

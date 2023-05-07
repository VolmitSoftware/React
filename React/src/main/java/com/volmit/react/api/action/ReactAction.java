package com.volmit.react.api.action;

import lombok.Getter;

public abstract class ReactAction<T extends ActionParams> implements Action<T> {
    @Getter
    private final String id;

    public ReactAction(String id) {
        this.id = id;
    }

    @Override
    public ActionTicket<T> create(T params) {
        return new ActionTicket<>(this, params)
                .onStart(() -> {
                })
                .onComplete(() -> {
                });
    }
}

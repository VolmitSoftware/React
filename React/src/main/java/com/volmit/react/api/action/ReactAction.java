package com.volmit.react.api.action;

import lombok.Getter;

public abstract class ReactAction<T extends ActionParams> implements Action<T> {
    @Getter
    private transient final String aid;

    public ReactAction(String id) {
        this.aid = id;
    }

    @Override
    public ActionTicket<T> create(T params) {
        return new ActionTicket<>(this, params);
    }

    public String getId() {
        return aid;
    }
}

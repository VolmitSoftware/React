package com.volmit.react.api.action;

public interface Action<T extends ActionParams>
{
    ActionTicket<T> create(T params);

    default ActionTicket<T> create() {
        return create(getDefaultParams());
    }

    void workOn(ActionTicket<T> ticket);

    String getId();

    T getDefaultParams();

    void onInit();
}

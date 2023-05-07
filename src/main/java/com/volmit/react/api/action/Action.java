package com.volmit.react.api.action;

import art.arcane.curse.Curse;
import art.arcane.curse.model.FuzzyMethod;
import com.volmit.react.api.arguments.Argument;

import java.util.List;

public interface Action<T extends ActionParams> {
    ActionTicket<T> create(T params);

    default ActionTicket<?> createForceful(Object params) {
        return Curse.on(this).method("create", Object.class).invoke(params);
    }

    default ActionTicket<T> create() {
        return create(getDefaultParams());
    }

    default T toParams(String[] args) throws Exception {
        T t = getDefaultParams();
        return (T) Argument.ArgumentProcessor.process(t.getClass(), args);
    }

    void workOn(ActionTicket<T> ticket);

    String getId();

    T getDefaultParams();

    void onInit();
}

package com.volmit.react.api.action;

import art.arcane.curse.Curse;
import art.arcane.curse.model.FuzzyMethod;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.plugin.VolmitSender;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface Action<T extends ActionParams> {
    ActionTicket<T> create(T params);

    default String getCompletedMessage(ActionTicket<T> ticket)
    {
        return "Completed " + getName() + " in " + Form.duration(ticket.getDuration(), 1);
    }

    default String getName() {
        return Form.capitalizeWords(getId().replace("-", " "));
    }

    default ActionTicket<T> create(T params, VolmitSender sender) {
        sender.sendMessage("Queued " + getName());
        return create(params)
            .onStart((i) -> sender.sendMessage("Starting " + getName()))
            .onComplete((i) -> sender.sendMessage(getCompletedMessage(i)));
    }

    default ActionTicket<?> createForceful(Object params) {
        return Curse.on(this).method("create", Object.class).invoke(params);
    }

    default ActionTicket<T> create() {
        return create(getDefaultParams());
    }

    void workOn(ActionTicket<T> ticket);

    String getId();

    T getDefaultParams();

    void onInit();
}

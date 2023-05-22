package com.volmit.react.core.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.content.action.ActionUnknown;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActionController extends TickedObject implements IController {
    private transient final List<ActionTicket<?>> ticketQueue = new ArrayList<>();
    private transient final List<ActionTicket<?>> ticketRuntime = new ArrayList<>();
    private transient Registry<Action<?>> actions;
    private int actionSpeedMultiplier;

    public ActionController() {
        super("react", "action",  100);
        start();
    }

    @Override
    public String getName() {
        return "Action";
    }

    public void queueAction(ActionTicket<?> ticket) {
        synchronized (ticketQueue) {
            ticketQueue.add(ticket);
        }
    }

    public <T extends ActionParams> Action<T> getAction(String id) {
        return actions.get(id);
    }

    @Override
    public void start() {
        actionSpeedMultiplier = 128;
        actions = new Registry<>(Action.class, "com.volmit.react.content.action");
    }

    public void postStart() {
        actions.all().forEach(Action::onInit);
        React.info("Registered " + actions.size() + " Actions");
    }

    @Override
    public void stop() {

    }

    @Override
    public void onTick() {
        synchronized (ticketQueue) {
            if (!ticketQueue.isEmpty() && ticketRuntime.size() < Math.max(3, Runtime.getRuntime().availableProcessors() / 4)) {
                ActionTicket<?> t = ticketQueue.remove(0);
                t.start();
                ticketRuntime.add(t);
                React.info("Action " + t.getAction().getId() + " started");
            }
        }

        synchronized (ticketRuntime) {
            if (!ticketRuntime.isEmpty()) {
                for (ActionTicket<?> i : new ArrayList<>(ticketRuntime)) {
                    Curse.on(i.getAction()).method("workOn", ActionTicket.class).invoke(i);
                }

                for (ActionTicket<?> i : new ArrayList<>(ticketRuntime)) {
                    if (i.isDone()) {
                        long runtime = System.currentTimeMillis() - i.getStartedAt();
                        ticketRuntime.remove(i);
                        React.success("Action " + i.getAction().getId() + " completed in " + Form.duration(runtime, 1));
                    }
                }
            }
        }
    }
}

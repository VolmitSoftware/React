package com.volmit.react.core.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.content.action.ActionUnknown;
import com.volmit.react.util.Form;
import com.volmit.react.util.IController;
import com.volmit.react.util.JarScanner;
import lombok.Data;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ActionController implements IController {
    private final List<ActionTicket<?>> ticketQueue = new ArrayList<>();
    private final List<ActionTicket<?>> ticketRuntime = new ArrayList<>();
    private Map<String, Action<?>> actions;
    private Action<?> unknown;
    private int actionSpeedMultiplier;

    @Override
    public String getName() {
        return "Action";
    }

    public void queueAction(ActionTicket<?> ticket) {
        synchronized (ticketQueue) {
            ticketQueue.add(ticket);
        }
    }

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage();

        if(m.startsWith("/")) {
            m = m.substring(1);
        }

        if(m.toLowerCase().startsWith("react ") || m.toLowerCase().startsWith("re ")) {
            String[] ar = m.split(" ");
            List<String> args = new ArrayList<>(Arrays.asList(ar));

            if(args.size() > 2) {

                String n = args.remove(0);

                if(n.equalsIgnoreCase("act")) {
                    String action = args.remove(0);
                    String[] a = args.toArray(new String[args.size()]);
                    try {
                        callAction(action, a);
                    } catch(Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    e.setCancelled(true);
                }
            }
        }
    }

    public <T extends ActionParams> Action<T> getAction(String id) {
        Action<?> s = actions.get(id);

        s = s == null ? unknown : s;

        if (s == null) {
            s = new ActionUnknown();
        }

        return (Action<T>) s;
    }

    public void callAction(String actionId, String[] arguments) throws Exception {
        Action<?> action = React.instance.getActionController().getAction(actionId);
        action.createForceful(action.toParams(arguments)).queue();
    }

    @Override
    public void start() {
        actionSpeedMultiplier = 128;
        actions = new HashMap<>();
        actions.put(ActionUnknown.ID, new ActionUnknown());
        String p = React.instance.jar().getAbsolutePath();
        p = p.replaceAll("\\Q.jar.jar\\E", ".jar");

        JarScanner j = new JarScanner(new File(p), "com.volmit.react.content.action");
        try {
            j.scan();
            j.getClasses().stream()
                    .filter(i -> i.isAssignableFrom(Action.class) || Action.class.isAssignableFrom(i))
                    .map((i) -> {
                        try {
                            return (Action<?>) i.getConstructor().newInstance();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        return null;
                    })
                    .forEach((i) -> {
                        if (i != null) {
                            actions.put(i.getId(), i);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void postStart() {
        actions.values().forEach(Action::onInit);
        React.info("Registered " + actions.size() + " Actions");
    }

    @Override
    public void stop() {

    }

    @Override
    public void tick() {
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

    @Override
    public int getTickInterval() {
        return -1;
    }
}

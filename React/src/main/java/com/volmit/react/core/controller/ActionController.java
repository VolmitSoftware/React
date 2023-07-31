/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.core.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActionController extends TickedObject implements IController {
    private transient final List<ActionTicket<?>> ticketQueue = new ArrayList<>();
    private transient final List<ActionTicket<?>> ticketRuntime = new ArrayList<>();
    private transient Registry<Action<?>> actions;
    private int actionSpeedMultiplier;

    public ActionController() {
        super("react", "action", 100);
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
        actions.all().forEach(((a) -> {
            if (a instanceof Listener l) {
                React.instance.registerListener(l);
            }
        }));
        React.info("Registered " + actions.size() + " Actions");
    }

    @Override
    public void stop() {
        actions.all().forEach(((a) -> {
            if (a instanceof Listener l) {
                React.instance.unregisterListener(l);
            }
        }));
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

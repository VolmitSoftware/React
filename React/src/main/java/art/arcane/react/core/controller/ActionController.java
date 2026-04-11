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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.format.Form;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

import java.util.*;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActionController extends TickedObject implements IController {
  private transient final Deque<ActionTicket<?>> ticketQueue = new ArrayDeque<>();
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
    if (ticket == null || ticket.getAction() == null) {
      return;
    }

    if (!ticket.getAction().isEnabled()) {
      React.verbose("Ignored queue request for disabled action: " + ticket.getAction().getId());
      return;
    }

    synchronized (ticketQueue) {
      ticketQueue.addLast(ticket);
    }
  }

  public <T extends ActionParams> Action<T> getAction(String id) {
    return actions.get(id);
  }

  @Override
  public void start() {
    actionSpeedMultiplier = 128;
    actions = new Registry<>(Action.class, "art.arcane.react.content.action");
  }

  public void postStart() {
    int enabledActions = 0;
    for (Action<?> action : actions.all()) {
      if (!action.isEnabled()) {
        React.verbose("Action disabled by config: " + action.getId());
        continue;
      }

      action.onInit();
      if (action instanceof Listener l) {
        React.instance.registerListener(l);
      }

      enabledActions++;
    }

    React.info("Registered " + actions.size() + " Actions (" + enabledActions + " enabled)");
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
    ActionTicket<?> next = null;
    synchronized (ticketQueue) {
      if (!ticketQueue.isEmpty() && ticketRuntime.size() < maxRuntimeActions()) {
        next = ticketQueue.pollFirst();
      }
    }

    if (next != null) {
      if (!next.getAction().isEnabled()) {
        React.verbose("Skipping queued action because it is now disabled: " + next.getAction().getId());
      } else {
        next.start();
        synchronized (ticketRuntime) {
          ticketRuntime.add(next);
        }
        React.info("Action " + next.getAction().getId() + " started");
      }
    }

    synchronized (ticketRuntime) {
      if (ticketRuntime.isEmpty()) {
        return;
      }

      Iterator<ActionTicket<?>> iterator = ticketRuntime.iterator();
      while (iterator.hasNext()) {
        ActionTicket<?> ticket = iterator.next();
        invoke(ticket);

        if (ticket.isDone()) {
          long runtime = System.currentTimeMillis() - ticket.getStartedAt();
          iterator.remove();
          React.success("Action " + ticket.getAction().getId() + " completed in " + Form.duration(runtime, 1));
        }
      }
    }
  }

  private int maxRuntimeActions() {
    return Math.max(3, Runtime.getRuntime().availableProcessors() / 4);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void invoke(ActionTicket<?> ticket) {
    Action action = ticket.getAction();
    action.workOn((ActionTicket) ticket);
  }
}

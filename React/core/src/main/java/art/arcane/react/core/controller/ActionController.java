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

import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.format.Form;
import lombok.Data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class ActionController implements IController {
  private static final long CONTROLLER_TICK_MS = 100L;
  private transient final Deque<ActionTicket<?>> ticketQueue = new ArrayDeque<>();
  private transient final List<ActionTicket<?>> ticketRuntime = new ArrayList<>();
  private transient final AtomicBoolean acceptingTickets = new AtomicBoolean(true);
  private transient Registry<Action<?>> actions;
  private int actionSpeedMultiplier;
  private transient Subscription controllerTicket = Subscription.NONE;

  @Override
  public String getId() {
    return "action";
  }

  @Override
  public String getName() {
    return "Action";
  }

  public void queueAction(ActionTicket<?> ticket) {
    if (ticket == null || ticket.getAction() == null) {
      return;
    }

    if (ticket.isDone()) {
      return;
    }

    if (!ticket.getAction().isEnabled()) {
      ticket.fail(new IllegalStateException("Action is disabled: " + ticket.getAction().getId()));
      ReactComponents.verbose("Ignored queue request for disabled action: " + ticket.getAction().getId());
      return;
    }

    synchronized (ticketQueue) {
      if (!acceptingTickets.get()) {
        ticket.fail(new IllegalStateException("Action controller is stopped"));
        return;
      }
      ticketQueue.addLast(ticket);
    }
  }

  public <T extends ActionParams> Action<T> getAction(String id) {
    return actions.get(id);
  }

  @Override
  public void start() {
    acceptingTickets.set(true);
    actionSpeedMultiplier = 128;
    actions = new Registry<>(Action.class, "art.arcane.react.content.action");
    ReactComponents.bind(this);
    controllerTicket = ReactComponents.host()
        .map(host -> host.schedule(this, "action", CONTROLLER_TICK_MS, this::onTick))
        .orElse(Subscription.NONE);
  }

  public void postStart() {
    Optional<ComponentHost> host = ReactComponents.host();
    int enabledActions = 0;
    for (Action<?> action : actions.all()) {
      if (!action.isEnabled()) {
        ReactComponents.verbose("Action disabled by config: " + action.getId());
        continue;
      }

      action.onInit();
      host.ifPresent(h -> h.attach(action));

      enabledActions++;
    }

    ReactComponents.verbose("Registered " + actions.size() + " Actions (" + enabledActions + " enabled)");
  }

  @Override
  public void stop() {
    acceptingTickets.set(false);
    controllerTicket.cancel();
    controllerTicket = Subscription.NONE;
    failPendingTickets();
    ComponentHost host = ReactComponents.host().orElse(null);
    if (actions != null && host != null) {
      actions.all().forEach(host::detach);
    }
    ReactComponents.unbind(this);
  }

  public void onTick() {
    reapTerminalTickets();
    if (!acceptingTickets.get()) {
      return;
    }

    ActionTicket<?> next = null;
    if (hasRuntimeCapacity()) {
      synchronized (ticketQueue) {
        if (acceptingTickets.get()) {
          next = ticketQueue.pollFirst();
        }
      }
    }

    if (next != null) {
      startTicket(next);
    }

    List<ActionTicket<?>> snapshot;
    synchronized (ticketRuntime) {
      if (ticketRuntime.isEmpty()) {
        return;
      }

      snapshot = new ArrayList<>(ticketRuntime);
    }

    for (ActionTicket<?> ticket : snapshot) {
      if (ticket == null || ticket.isDone()) {
        continue;
      }

      if (!ticket.isRunning()) {
        ticket.fail(new IllegalStateException("Runtime action ticket is not running"));
        continue;
      }

      try {
        invoke(ticket);
      } catch (Throwable throwable) {
        ticket.fail(throwable);
        ReactComponents.reportError("Action " + ticket.getAction().getId() + " failed", throwable);
      }
    }

    reapTerminalTickets();
  }

  private void failPendingTickets() {
    IllegalStateException failure = new IllegalStateException("Action controller stopped before ticket completion");
    synchronized (ticketQueue) {
      ActionTicket<?> ticket;
      while ((ticket = ticketQueue.pollFirst()) != null) {
        ticket.fail(failure);
      }
    }

    synchronized (ticketRuntime) {
      for (ActionTicket<?> ticket : ticketRuntime) {
        if (ticket != null) {
          ticket.fail(failure);
        }
      }
      ticketRuntime.clear();
    }
  }

  private void startTicket(ActionTicket<?> ticket) {
    if (ticket.isDone()) {
      return;
    }

    if (!ticket.getAction().isEnabled()) {
      ticket.fail(new IllegalStateException("Action was disabled before it started: " + ticket.getAction().getId()));
      ReactComponents.verbose("Skipping queued action because it is now disabled: " + ticket.getAction().getId());
      return;
    }

    try {
      ticket.start();
    } catch (Throwable throwable) {
      ticket.fail(throwable);
      ReactComponents.reportError("Action " + ticket.getAction().getId() + " failed to start", throwable);
      return;
    }

    if (ticket.isDone() || !ticket.isRunning()) {
      logTerminalTicket(ticket);
      return;
    }

    synchronized (ticketRuntime) {
      if (!acceptingTickets.get()) {
        ticket.fail(new IllegalStateException("Action controller stopped before ticket startup completed"));
        return;
      }

      for (ActionTicket<?> running : ticketRuntime) {
        if (running == ticket) {
          return;
        }
      }
      ticketRuntime.add(ticket);
    }

    ReactComponents.verbose("Action " + ticket.getAction().getId() + " started");
  }

  private boolean hasRuntimeCapacity() {
    synchronized (ticketRuntime) {
      return ticketRuntime.size() < maxRuntimeActions();
    }
  }

  private void reapTerminalTickets() {
    List<ActionTicket<?>> terminal = null;
    synchronized (ticketRuntime) {
      for (int index = ticketRuntime.size() - 1; index >= 0; index--) {
        ActionTicket<?> ticket = ticketRuntime.get(index);
        if (ticket != null && !ticket.isDone()) {
          continue;
        }

        ticketRuntime.remove(index);
        if (ticket != null) {
          if (terminal == null) {
            terminal = new ArrayList<>();
          }
          terminal.add(ticket);
        }
      }
    }

    if (terminal == null) {
      return;
    }

    for (ActionTicket<?> ticket : terminal) {
      logTerminalTicket(ticket);
    }
  }

  private void logTerminalTicket(ActionTicket<?> ticket) {
    long runtime = ticket.getStartedAt() == 0
        ? 0
        : Math.max(0, ticket.getCompletedAt() - ticket.getStartedAt());
    if (ticket.isFailed()) {
      Throwable failure = ticket.getFailure();
      String detail = failure == null ? "unknown failure" : failure.getClass().getSimpleName()
          + (failure.getMessage() == null ? "" : " - " + failure.getMessage());
      ReactPlatform platform = ReactPlatforms.getOrNull();
      if (platform != null) {
        platform.warn("Action " + ticket.getAction().getId() + " failed after "
            + Form.duration(runtime, 1) + ": " + detail, failure);
      }
      return;
    }

    ReactComponents.verbose("Action " + ticket.getAction().getId() + " completed in " + Form.duration(runtime, 1));
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

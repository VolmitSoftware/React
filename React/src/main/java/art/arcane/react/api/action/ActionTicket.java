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

package art.arcane.react.api.action;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.react.React;
import art.arcane.react.core.controller.ActionController;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Data
public class ActionTicket<T extends ActionParams> {
  private Action<T> action;
  private T params;
  private volatile boolean done;
  private volatile boolean failed;
  private volatile boolean running;
  private volatile Throwable failure;
  private long completedAt;
  private double duration;
  private long startedAt;
  private List<Consumer<ActionTicket<T>>> onComplete;
  private List<Consumer<ActionTicket<T>>> onStart;
  private List<Consumer<ActionTicket<T>>> onTerminal;
  private boolean terminalCallbacksInvoked;
  private int work;
  private int totalWork;
  private int count;
  private PrecisionStopwatch psw;

  public ActionTicket(Action<T> action, T params) {
    this.action = action;
    this.params = params;
    this.onComplete = new ArrayList<>();
    this.onStart = new ArrayList<>();
    this.onTerminal = new ArrayList<>();
    this.startedAt = 0;
    this.completedAt = 0;
    work = 0;
    duration = -1;
    totalWork = 1;
    count = 0;
  }

  public void addCount(int c) {
    count += c;
  }

  public void addCount() {
    count++;
  }

  public void addWork(int w) {
    work += w;
  }

  public void addWork() {
    work++;
  }

  public double getProgress() {
    return getWork() / (double) getTotalWork();
  }

  public synchronized ActionTicket<T> onComplete(Consumer<ActionTicket<T>> r) {
    onComplete.add(Objects.requireNonNull(r, "callback"));
    return this;
  }

  public synchronized ActionTicket<T> onStart(Consumer<ActionTicket<T>> r) {
    onStart.add(Objects.requireNonNull(r, "callback"));
    return this;
  }

  public synchronized ActionTicket<T> onTerminal(Consumer<ActionTicket<T>> r) {
    onTerminal.add(Objects.requireNonNull(r, "callback"));
    return this;
  }

  public void queue() {
    React.controller(ActionController.class).queueAction(this);
  }

  public void start() {
    List<Consumer<ActionTicket<T>>> callbacks;
    synchronized (this) {
      if (done || running) {
        return;
      }

      psw = PrecisionStopwatch.start();
      startedAt = System.currentTimeMillis();
      running = true;
      callbacks = List.copyOf(onStart);
    }

    invokeCallbacks(callbacks);
  }

  public void complete() {
    List<Consumer<ActionTicket<T>>> callbacks;
    synchronized (this) {
      if (done) {
        return;
      }

      finish();
      callbacks = List.copyOf(onComplete);
    }

    invokeCallbacks(callbacks);
    invokeTerminalCallbacks();
  }

  public void fail(Throwable throwable) {
    Throwable failureCause = Objects.requireNonNull(throwable, "throwable");
    synchronized (this) {
      if (failed || done && terminalCallbacksInvoked) {
        return;
      }

      if (!done) {
        finish();
      }
      failure = failureCause;
      failed = true;
    }
    invokeTerminalCallbacks();
  }

  private void finish() {
    duration = psw == null ? 0D : psw.getMilliseconds();
    completedAt = System.currentTimeMillis();
    running = false;
    done = true;
  }

  private void invokeCallbacks(List<Consumer<ActionTicket<T>>> callbacks) {
    try {
      for (Consumer<ActionTicket<T>> callback : callbacks) {
        callback.accept(this);
      }
    } catch (Throwable throwable) {
      fail(throwable);
      if (throwable instanceof Error error) {
        throw error;
      }
      if (throwable instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Action ticket callback failed", throwable);
    }
  }

  private void invokeTerminalCallbacks() {
    List<Consumer<ActionTicket<T>>> callbacks;
    synchronized (this) {
      if (terminalCallbacksInvoked) {
        return;
      }
      terminalCallbacksInvoked = true;
      callbacks = List.copyOf(onTerminal);
    }

    for (Consumer<ActionTicket<T>> callback : callbacks) {
      try {
        callback.accept(this);
      } catch (Throwable throwable) {
        React.reportError(new IllegalStateException("Action ticket terminal callback failed", throwable));
      }
    }
  }
}

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

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.command.CommandSender;

public interface Action<T extends ActionParams> extends Registered {
  ActionTicket<T> create(T params);

  default String getCompletedMessage(ActionTicket<T> ticket) {
    return ReactLanguage.plain(
        ActionMessages.COMPLETED,
        MessageArgument.untrusted("action", getName()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  default String getConfigCategory() {
    return "action";
  }

  default boolean isEnabled() {
    return true;
  }

  default ActionTicket<T> create(T params, CommandSender sender) {
    if (!isEnabled()) {
      ReactLanguage.sendPrefixed(
          sender,
          RuntimeMessages.ACTION_DISABLED,
          MessageArgument.untrusted("action", getName())
      );
      return create(params);
    }

    ReactLanguage.sendPrefixed(sender, RuntimeMessages.ACTION_QUEUED, MessageArgument.untrusted("action", getName()));
    return create(params)
        .onStart((i) -> ReactLanguage.sendPrefixed(
            sender,
            RuntimeMessages.ACTION_STARTING,
            MessageArgument.untrusted("action", getName())
        ))
        .onComplete((i) -> ReactLanguage.sendPrefixed(
            sender,
            RuntimeMessages.ACTION_COMPLETED,
            MessageArgument.untrusted("message", getCompletedMessage(i))
        ));
  }

  @SuppressWarnings("unchecked")
  default ActionTicket<?> createForceful(Object params) {
    return create((T) params);
  }

  default ActionTicket<T> create() {
    return create(getDefaultParams());
  }

  void workOn(ActionTicket<T> ticket);

  T getDefaultParams();

  void onInit();
}

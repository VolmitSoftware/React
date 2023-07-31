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

package com.volmit.react.api.action;

import art.arcane.curse.Curse;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.registry.Registered;
import org.bukkit.command.CommandSender;

public interface Action<T extends ActionParams> extends Registered {
    ActionTicket<T> create(T params);

    default String getCompletedMessage(ActionTicket<T> ticket) {
        return "Completed " + getName() + " in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    default String getConfigCategory() {
        return "action";
    }

    default ActionTicket<T> create(T params, CommandSender sender) {
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

    T getDefaultParams();

    void onInit();
}

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

import lombok.Getter;

public abstract class ReactAction<T extends ActionParams> implements Action<T> {
    @Getter
    private transient final String aid;

    public ReactAction(String id) {
        this.aid = id;
    }

    @Override
    public ActionTicket<T> create(T params) {
        return new ActionTicket<>(this, params);
    }

    public String getId() {
        return aid;
    }
}

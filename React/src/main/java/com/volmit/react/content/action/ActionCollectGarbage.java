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

package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.content.sampler.SamplerMemoryUsed;
import com.volmit.react.util.format.Form;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

public class ActionCollectGarbage extends ReactAction<ActionCollectGarbage.Params> {
    public static final String ID = "collect-garbage";
    public static final String SHORT = "gc";

    public ActionCollectGarbage() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Freed " + React.sampler(SamplerMemoryUsed.ID).format(ticket.getCount()) + " in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        int bytesBefore = (int) React.sampler(SamplerMemoryUsed.ID).sample();
        System.gc();
        int bytesAfter = (int) React.sampler(SamplerMemoryUsed.ID).sample();

        if (bytesBefore > bytesAfter) {
            ticket.setCount(bytesBefore - bytesAfter);
        }

        ticket.complete();
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    @Accessors(chain = true)
    public static class Params implements ActionParams {

    }
}

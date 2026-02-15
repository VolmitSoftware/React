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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.content.sampler.SamplerMemoryUsed;
import art.arcane.volmlib.util.format.Form;
import art.arcane.react.util.config.ConfigDoc;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@art.arcane.react.util.config.ConfigDescription("Configuration for Collect Garbage action. Requests JVM garbage collection and reports immediate heap reclaimed.")
public class ActionCollectGarbage extends ReactAction<ActionCollectGarbage.Params> {
    public static final String ID = "collect-garbage";
    public static final String SHORT = "gc";

    public ActionCollectGarbage() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        Params params = ticket.getParams();
        if (params.getFreedBytes() <= 0L) {
            return "GC complete, no immediate heap reclaimed (" + Form.memSize(params.getAfterBytes()) + " used) in " + Form.duration(ticket.getDuration(), 1);
        }

        return "Freed " + Form.memSize(params.getFreedBytes())
                + " (" + Form.memSize(params.getBeforeBytes()) + " -> " + Form.memSize(params.getAfterBytes()) + ") in "
                + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        Params params = ticket.getParams();
        if (!params.isGcRequested()) {
            params.setBeforeBytes(sampleMemoryBytes());
            params.setGcRequested(true);
            requestGc();
            return;
        }

        if (params.getWaitedTicks() < params.getPostGcWaitTicks()) {
            params.setWaitedTicks(params.getWaitedTicks() + 1);
            return;
        }

        long bytesAfter = sampleMemoryBytes();
        if (!params.isSecondPassRequested() && bytesAfter >= params.getBeforeBytes()) {
            params.setSecondPassRequested(true);
            params.setWaitedTicks(0);
            requestGc();
            return;
        }

        params.setAfterBytes(bytesAfter);
        params.setFreedBytes(Math.max(0L, params.getBeforeBytes() - bytesAfter));
        ticket.setCount((int) Math.min(Integer.MAX_VALUE, params.getFreedBytes()));
        ticket.complete();
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    private long sampleMemoryBytes() {
        if (React.sampler(SamplerMemoryUsed.ID) == null) {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }

        return Math.max(0L, Math.round(React.sampler(SamplerMemoryUsed.ID).sample()));
    }

    private void requestGc() {
        System.gc();
    }

    @Builder
    @Data
    @Accessors(chain = true)
    public static class Params implements ActionParams {
        @Builder.Default
        @ConfigDoc(
                value = "Ticks to wait after requesting GC before sampling memory.",
                impact = "Higher values allow JVM collectors more time to settle; lower values report faster but may undercount reclaimed memory."
        )
        private int postGcWaitTicks = 2;
        @Builder.Default
        private transient boolean gcRequested = false;
        @Builder.Default
        private transient boolean secondPassRequested = false;
        @Builder.Default
        private transient int waitedTicks = 0;
        @Builder.Default
        private transient long beforeBytes = 0L;
        @Builder.Default
        private transient long afterBytes = 0L;
        @Builder.Default
        private transient long freedBytes = 0L;
    }
}

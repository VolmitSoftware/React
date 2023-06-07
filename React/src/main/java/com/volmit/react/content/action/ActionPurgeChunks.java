package com.volmit.react.content.action;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.core.controller.ActionController;
import com.volmit.react.model.AreaActionParams;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class ActionPurgeChunks extends ReactAction<ActionPurgeChunks.Params> {
    public static final String ID = "purge-chunks";
    public static final String SHORT = "pc";

    public ActionPurgeChunks() {
        super(ID);
    }

    @Edict.Command("/react action " + ID)
    @Edict.Aliases({"/react action " + SHORT, "/react a " + SHORT, "/react a " + ID})
    public static void command() {
        Action<ActionPurgeChunks.Params> pe = React.action(ID);
        ActionPurgeChunks.Params p = pe.getDefaultParams();
        pe.create(p, EdictContext.get().getSender()).queue();
    }

    @Edict.PlayerOnly
    @Edict.Command("/react action " + ID)
    @Edict.Aliases({"/react action " + SHORT, "/react a " + SHORT, "/react a " + ID})
    public static void command(World world) {
        Action<ActionPurgeChunks.Params> pe = React.action(ID);
        ActionPurgeChunks.Params p = pe.getDefaultParams().withWorld(world);
        pe.create(p, EdictContext.get().getSender()).queue();
    }

    List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
        List<Chunk> c = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            Chunk cc = ticket.getParams().getArea().popChunk();

            if (cc == null) {
                break;
            }

            c.add(cc);
        }

        return c;
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Purged " + ticket.getCount() + " Chunks in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = pullChunks(ticket, React.controller(ActionController.class).getActionSpeedMultiplier());

        if (ticket.getTotalWork() <= 1) {
            ticket.setTotalWork(ticket.getParams().getArea().getChunks().size());
        }

        if (c.isEmpty()) {
            ticket.complete();
        } else {
            for (Chunk i : c) {
                purge(i, ticket);
            }

            ticket.addWork(c.size());
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    private void purge(Chunk c, ActionTicket<Params> ticket) {
        J.s(c::unload);
        if (!c.isLoaded()) {
            ticket.addCount();
        }
    }


    @Builder
    @Data
    @Accessors(
            chain = true
    )
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @Builder.Default
        private AreaActionParams area = AreaActionParams.builder().build();

        public Params withWorld(World world) {
            area.setWorld(world.getName());
            area.setAllChunks(true);
            return this;
        }
    }
}

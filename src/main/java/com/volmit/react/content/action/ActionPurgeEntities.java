package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.api.model.AreaActionParams;
import com.volmit.react.api.model.FilterParams;
import com.volmit.react.util.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
    public static final String ID = "purge-entities";

    public ActionPurgeEntities() {
        super(ID);
    }

    List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
        List<Chunk> c = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            Chunk cc = J.sResult(() -> ticket.getParams().getArea().popChunk());
            if (cc == null) {
                break;
            }

            c.add(cc);
        }

        return c;
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = pullChunks(ticket, React.instance.getActionController().getActionSpeedMultiplier());

        if (ticket.getTotalWork() <= 1) {
            ticket.setTotalWork(ticket.getParams().getArea().getChunks().size() + c.size());
        }

        if (c.isEmpty()) {
            ticket.complete();
        } else {
            for (Chunk i : c) {
                purge(i, ticket.getParams());
            }

            ticket.addWork(c.size());
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder()
                .area(AreaActionParams.builder()
                        .allChunks(true)
                        .world("world")
                        .build())
                .build();
    }

    private void purge(Chunk c, Params purgeEntitiesParams) {

    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @Builder.Default
        private AreaActionParams area = new AreaActionParams();
        @Builder.Default
        private FilterParams<EntityType> filter = new FilterParams<>();
    }
}

package com.volmit.react.action;

import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ActionUnknown extends ReactAction<ActionUnknown.Params>
{
    public static final String ID = "unknown";

    public ActionUnknown() {
        super(ID);
    }

    @Override
    public Params getDefaultParams() {
        return new Params();
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {

    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    public static class Params implements ActionParams {

    }
}

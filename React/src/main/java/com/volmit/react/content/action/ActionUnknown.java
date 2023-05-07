package com.volmit.react.content.action;

import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import lombok.Builder;
import lombok.Data;

public class ActionUnknown extends ReactAction<ActionUnknown.Params> {
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

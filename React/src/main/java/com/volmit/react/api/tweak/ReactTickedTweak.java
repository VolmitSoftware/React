package com.volmit.react.api.tweak;

import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReactTickedTweak extends TickedObject {
    private final Tweak component;

    public ReactTickedTweak(Tweak component) {
        super("react", "tweak-" + component.getId(), component.getTickInterval());
        this.component = component;
    }

    @Override
    public void onTick() {
        component.onTick();
    }
}

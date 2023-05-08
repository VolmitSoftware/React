package com.volmit.react.api.feature;

import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReactTickedFeature extends TickedObject {
    private final Feature component;

    public ReactTickedFeature(Feature component) {
        super("react", "feature-" + component.getId(), component.getTickInterval());
        this.component = component;
    }

    @Override
    public void onTick() {
        component.onTick();
    }
}

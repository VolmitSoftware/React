package com.volmit.react.api.feature;

import com.volmit.react.util.format.Form;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class ReactFeature implements Feature {
    private transient final String id;
    private transient final String name;
    private boolean enabled = true;

    public ReactFeature(String id)
    {
        this.id = id;
        this.name = Form.capitalizeWords(id.replace("-", " "));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

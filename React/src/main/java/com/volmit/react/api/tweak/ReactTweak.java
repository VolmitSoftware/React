package com.volmit.react.api.tweak;

import com.volmit.react.util.format.Form;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class ReactTweak implements Tweak {
    private transient final String id;
    private transient final String name;
    private boolean enabled = true;

    public ReactTweak(String id) {
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

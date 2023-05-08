package com.volmit.react.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilterParams<T> {
    @Singular
    protected Set<T> types;

    @Builder.Default
    protected boolean blacklist = false;

    public boolean allows(T t) {
        if(types == null || types.isEmpty()) {
            return blacklist;
        }

        if(blacklist) {
            return !types.contains(t);
        }

        return types.contains(t);
    }
}

package com.volmit.react.api.model;

import lombok.*;

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
        if (types == null || types.isEmpty()) {
            return blacklist;
        }

        if (blacklist) {
            return !types.contains(t);
        }

        return types.contains(t);
    }
}

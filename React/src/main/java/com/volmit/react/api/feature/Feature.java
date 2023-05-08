package com.volmit.react.api.feature;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedField;
import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.api.ReactComponent;
import org.bukkit.Material;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collectors;

public interface Feature extends ReactComponent {
    void onActivate();

    boolean isEnabled();

    void onDeactivate();

    int getTickInterval();

    void onTick();

    @Override
    default String getConfigCategory() {
        return "feature";
    }
}

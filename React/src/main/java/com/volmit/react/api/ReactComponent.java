package com.volmit.react.api;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedField;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.react.React;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.io.IO;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;

public interface ReactComponent {
    default String getConfigCategory() {
        return "component";
    }

    default String getName() {
        return Form.capitalizeWords(getId().replace("-", " "));
    }

    default Material getIcon() {
        return Material.SLIME_BALL;
    }

    String getId();

    default void loadConfiguration() {
        File configFile = React.instance.getDataFile(getConfigCategory(), getId() + ".json");
        if (!configFile.exists()) {
            try {
                IO.writeAll(configFile, new Gson().toJson(this));
                React.verbose("Creating a default config for " + getName() + " in " + configFile.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            CursedComponent loaded = Curse.on(new Gson().fromJson(IO.readAll(configFile), getClass()));
            CursedComponent c = Curse.on(this);
            c.instanceFields().filter(i -> !i.isFinal() && !i.isTransient()).forEach((self) -> {
                CursedField from = loaded.field(self.field().getName());
                Object oFrom = from.get();

                if (oFrom != null) {
                    self.set(oFrom);
                }
            });
            React.verbose("Loaded config for " + getName() + " in " + configFile.getPath());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            IO.writeAll(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

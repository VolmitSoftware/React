package com.volmit.react.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class YamlDataInput implements IDataInput {
    @Override
    public DataCluster read(File f) {
        DataCluster d = new DataCluster();

        try {
            FileConfiguration fc = new YamlConfiguration();
            fc.load(f);
            d.fromFileConfiguration(fc);
        } catch (Throwable e) {
            Ex.t(e);
        }

        return d;
    }
}

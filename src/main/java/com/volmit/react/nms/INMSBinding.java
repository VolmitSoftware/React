package com.volmit.react.nms;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import primal.bukkit.world.MaterialBlock;

public interface INMSBinding {
    String getPackageVersion();

    void updateBlock(Block b);

    void setBlock(Location l, MaterialBlock m);

    void merge(Entity drop, Entity into);
}

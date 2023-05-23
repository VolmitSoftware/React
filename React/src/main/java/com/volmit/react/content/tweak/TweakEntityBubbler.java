package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

public class TweakEntityBubbler extends ReactTweak implements Listener {
    public static final String ID = "entity-bubbler";
    /**
     * The maximum crowding factor an entity can have before it is purged
     */
    private int maxEntityCrowding = 10;
    /**
     * List of entity types to check for crowding
     */
    private List<EntityType> entityTypes = Arrays.asList(EntityType.ARROW, EntityType.ARMOR_STAND, EntityType.MINECART);
    /**
     * Prevents the entity from existing if its being suspended by the soulsand bubble column
     */
    private boolean preventEntityBubbling = true;

    public TweakEntityBubbler() {
        super(ID);
    }

    @Override
    public void onActivate() {
        for (EntityType entityType : entityTypes) {
            React.controller(EntityController.class).registerEntityTickListener(entityType, this::onCrowdCheck);
        }
    }

    public void onCrowdCheck(Entity entity) {
        // Get the crowding factor of the entity when its ticked
        double crowdingFactor = ReactEntity.getCrowding(entity);
        //purge the entity if its crowding factor is higher than the maxEntityCrowding
        if (crowdingFactor >= maxEntityCrowding) {
            kill(entity);
        }
        if (preventEntityBubbling) {
            // If the entity is being bubbled, kill it
            if (isEntityBubbled(entity)) {
                kill(entity);
            }
        }

    }

    public boolean isEntityBubbled(Entity entity) {
        Location location = entity.getLocation();
        World world = location.getWorld();
        if (world == null)
            return false;
        Block block = world.getBlockAt(location);
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.isLiquid() || block.isLiquid()) {
            if (block.getType().equals(Material.BUBBLE_COLUMN) || blockBelow.getType().equals(Material.BUBBLE_COLUMN)) {
                return true;
            } else if (block.getType().equals(Material.SOUL_SAND) || blockBelow.getType().equals(Material.SOUL_SAND)) {
                return true;
            }
        }
        return false;
    }


    private void kill(Entity entity) {
        J.s(() -> React.kill(entity), (int) (20 * Math.random()));
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}

package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private int maxEntityCrowding = 15;
    /**
     * List of entity types to check for crowding
     */
    private List<EntityType> entityTypes = Arrays.asList(EntityType.ARROW, EntityType.ARMOR_STAND);
    /**
     * Prevents the entity from existing if its being suspended by the soulsand bubble column
     */
    private boolean preventEntityBubbling = true;

    public TweakEntityBubbler() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
        for (EntityType entityType : entityTypes) {
            React.instance.getEntityController().registerEntityTickListener(entityType, this::onCrowdCheck);
        }
    }

    public void onCrowdCheck(Entity entity) {
        // Get the crowding factor of the entity when its ticked
        double crowdingFactor = ReactEntity.getCrowding(entity);
        //purge the entity if its crowding factor is higher than the maxEntityCrowding
        if (crowdingFactor >= maxEntityCrowding) {
            kill(entity);
        } else if (preventEntityBubbling) {
            // If the entity is being bubbled, kill it
            if (isEntityBubbled(entity)) {
                kill(entity);
            }
        }

    }

    public boolean isEntityBubbled(Entity entity) {
        Location location = entity.getLocation();
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int y = location.getBlockY();

        for (int i = y; i >= 0; i--) {
            Block block = world.getBlockAt(x, i, z);
            if (block.getType() != Material.AIR && block.getType() != Material.WATER && block.getType() != Material.SEAGRASS && block.getType() != Material.TALL_SEAGRASS) {
                return block.getType() == Material.SOUL_SAND || block.getType() == Material.MAGMA_BLOCK;
            }
        }
        return false;
    }


    private void kill(Entity entity) {
        J.s(() -> React.kill(entity), (int) (20 * Math.random()));
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}

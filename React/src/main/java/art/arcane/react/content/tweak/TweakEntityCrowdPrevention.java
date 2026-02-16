/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

@art.arcane.react.util.config.ConfigDescription("Configuration for Entity Crowd Prevention tweak. Trims overcrowded livestock-style entity clusters before they create sustained tick pressure.")
public class TweakEntityCrowdPrevention extends ReactTweak implements Listener {
    public static final String ID = "entity-crowd-prevention";
    /**
     * The maximum crowding factor an entity can have before it is purged
     */
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum entities allowed per cluster crowd in entity crowd prevention.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxEntitiesPerClusterCrowd = 10;
    /**
     * List of entity types to check for crowding
     */
    @art.arcane.react.util.config.ConfigDoc(value = "Entity types monitored for local crowding pressure and eligible for pruning.", impact = "Narrow this list to protect more mobs, or broaden it to enforce anti-crowding on additional entity types.")
    private List<EntityType> mobsToPreventFromCrowding = Arrays.asList(
            EntityType.COW,
            EntityType.CHICKEN,
            EntityType.PIG,
            EntityType.SHEEP,
            EntityType.PIG
            );
    /**
     * Prevents the entity from existing if its being suspended by the soulsand bubble column
     */
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether entity crowd prevention applies prevent entity bubbling.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean preventEntityBubbling = true;

    public TweakEntityCrowdPrevention() {
        super(ID);
    }

    @Override
    public void onActivate() {
        for (EntityType entityType : mobsToPreventFromCrowding) {
            React.controller(EntityController.class).registerEntityTickListener(entityType, this::onCrowdCheck);
        }
    }

    /**
     * Checks if the entity is crowded
     */
    public void onCrowdCheck(Entity entity) {
        // Get the crowding factor of the entity when its ticked
        double crowdingFactor = ReactEntity.getCrowding(entity);
        //purge the entity if its crowding factor is higher than the maxEntityCrowding
        if (crowdingFactor >= maxEntitiesPerClusterCrowd) {
            kill(entity);
        }

    }

    private void kill(Entity entity) {
        int delay = (int) (20 * Math.random());
        if (!J.runEntity(entity, () -> React.kill(entity, 3), delay)) {
            React.kill(entity, 3);
        }
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

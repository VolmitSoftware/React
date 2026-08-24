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
 */

package art.arcane.react.util.project.world;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntityKillerReconcileTest {
  @Test
  void reconcileStripsCountdownNameAndStampFromOriginallyUnnamedEntity() {
    Entity entity = Mockito.mock(Entity.class);
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE))).thenReturn((byte) 0);

    EntityKiller.reconcile(entity);

    Mockito.verify(container).remove(Mockito.any(NamespacedKey.class));
    Mockito.verify(entity).setCustomNameVisible(false);
    Mockito.verify(entity).setCustomName(null);
  }

  @Test
  void reconcilePreservesPlayerNameWhenCountdownWasAppliedToNamedEntity() {
    Entity entity = Mockito.mock(Entity.class);
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE))).thenReturn((byte) 1);

    EntityKiller.reconcile(entity);

    Mockito.verify(container).remove(Mockito.any(NamespacedKey.class));
    Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
    Mockito.verify(entity, Mockito.never()).setCustomNameVisible(Mockito.anyBoolean());
  }

  @Test
  void reconcileLeavesUnstampedEntityUntouched() {
    Entity entity = Mockito.mock(Entity.class);
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE))).thenReturn(null);

    EntityKiller.reconcile(entity);

    Mockito.verify(container, Mockito.never()).remove(Mockito.any(NamespacedKey.class));
    Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
    Mockito.verify(entity, Mockito.never()).setCustomNameVisible(Mockito.anyBoolean());
  }
}

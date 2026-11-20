package art.arcane.react.api.protect.internal;

import java.util.Set;

/** How the mod protection engine reads and tags the platform's entity objects. */
public interface ProtectionEntities {
  /** False when the object is not an entity of this platform. */
  boolean isEntity(Object entity);

  String typeId(Object entity);

  String worldName(Object entity);

  /** The entity's scoreboard tags. Claims are stored here so they persist with the entity. */
  Set<String> tags(Object entity);

  boolean addTag(Object entity, String tag);

  boolean removeTag(Object entity, String tag);
}

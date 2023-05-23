package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EnumeratedParser;
import org.bukkit.entity.EntityType;

public class EntityTypeParser implements EnumeratedParser<EntityType> {
    @Override
    public Class<? extends Enum<EntityType>> getEnumType() {
        return EntityType.class;
    }
}

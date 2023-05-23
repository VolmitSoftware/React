package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EnumeratedParser;
import org.bukkit.Sound;

public class SoundParser implements EnumeratedParser<Sound> {
    @Override
    public Class<? extends Enum<Sound>> getEnumType() {
        return Sound.class;
    }
}

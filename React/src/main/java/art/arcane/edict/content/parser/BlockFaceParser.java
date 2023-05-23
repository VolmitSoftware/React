package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EnumeratedParser;
import org.bukkit.block.BlockFace;

public class BlockFaceParser implements EnumeratedParser<BlockFace> {
    @Override
    public Class<? extends Enum<BlockFace>> getEnumType() {
        return BlockFace.class;
    }
}

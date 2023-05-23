package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EnumeratedParser;
import org.bukkit.Particle;

public class ParticleParser implements EnumeratedParser<Particle> {
    @Override
    public Class<? extends Enum<Particle>> getEnumType() {
        return Particle.class;
    }
}

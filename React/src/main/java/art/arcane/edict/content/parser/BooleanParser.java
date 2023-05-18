package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class BooleanParser implements EdictParser<Boolean> {
    @Override
    public EdictValue<Boolean> parse(String s) {
        boolean v = s.equalsIgnoreCase("true");

        if(v || s.equalsIgnoreCase("false")) {
            return high(v);
        }

        return low(v);
    }
}

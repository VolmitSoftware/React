package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class StringParser implements EdictParser<String> {
    @Override
    public EdictValue<String> parse(String s) {
        return high(s);
    }
}

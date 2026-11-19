package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.ControlItemDto;

import java.util.List;
import java.util.Map;

public interface ControlBackend {
    List<ControlItemDto> list();

    ControlItemDto get(String id);

    ControlItemDto setEnabled(String id, boolean enabled);

    ControlItemDto setKnobs(String id, Map<String, Object> knobs);
}

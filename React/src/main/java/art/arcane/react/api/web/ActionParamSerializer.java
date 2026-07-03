package art.arcane.react.api.web;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.ActionParamDto;
import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.util.project.config.ConfigDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActionParamSerializer {

    private final KnobSerializer knobSerializer;

    public ActionParamSerializer() {
        this.knobSerializer = new KnobSerializer();
    }

    public ActionParamSerializer(KnobSerializer knobSerializer) {
        this.knobSerializer = knobSerializer;
    }

    public List<ActionParamDto> params(Action<?> action) {
        Map<String, ActionParamDto> byKey = new LinkedHashMap<>();

        List<KnobDto> actionKnobs = knobSerializer.knobs(action);
        for (KnobDto knob : actionKnobs) {
            if (!byKey.containsKey(knob.key)) {
                byKey.put(knob.key, toActionParamDto(knob));
            }
        }

        ActionParams defaultParams = action.getDefaultParams();
        if (defaultParams != null) {
            List<KnobDto> paramsKnobs = knobSerializer.knobs(defaultParams);
            for (KnobDto knob : paramsKnobs) {
                if (!byKey.containsKey(knob.key)) {
                    byKey.put(knob.key, toActionParamDto(knob));
                }
            }
        }

        return new ArrayList<>(byKey.values());
    }

    public ActionDescriptorDto describe(Action<?> action) {
        ActionDescriptorDto dto = new ActionDescriptorDto();
        dto.id = action.getId();
        dto.name = action.getName();
        dto.description = extractDescription(action.getClass());
        dto.destructive = ReactActionCatalog.isDestructive(action.getId());
        List<ActionParamDto> paramList = params(action);
        dto.params = paramList.toArray(new ActionParamDto[0]);
        return dto;
    }

    private static ActionParamDto toActionParamDto(KnobDto knob) {
        ActionParamDto dto = new ActionParamDto();
        dto.key = knob.key;
        dto.label = knob.label;
        dto.type = knob.type;
        dto.options = knob.options;
        dto.defaultValue = knob.value;
        dto.required = (knob.value == null);
        return dto;
    }

    private static String extractDescription(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            ConfigDescription annotation = cursor.getDeclaredAnnotation(ConfigDescription.class);
            if (annotation != null) {
                return annotation.value();
            }
            cursor = cursor.getSuperclass();
        }
        return "";
    }
}

package art.arcane.react.api.web;

import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.core.gui.ReactGuiTaxonomy;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.registry.Registered;

import java.util.List;

public class ControlSerializer {

    private final KnobSerializer knobSerializer;

    public ControlSerializer(KnobSerializer knobSerializer) {
        this.knobSerializer = knobSerializer;
    }

    public ControlSerializer() {
        this(new KnobSerializer());
    }

    public ControlItemDto toDto(Registered item, boolean enabled) {
        ControlItemDto dto = new ControlItemDto();
        dto.id = item.getId();
        dto.name = item.getName();
        dto.category = ReactGuiTaxonomy.categoryForId(item.getId()).label();
        dto.description = extractDescription(item.getClass());
        dto.enabled = enabled;
        List<KnobDto> knobList = knobSerializer.knobs(item);
        dto.knobs = knobList.toArray(new KnobDto[0]);
        return dto;
    }

    public ControlItemDto toDto(Feature f) {
        return toDto(f, f.isEnabled());
    }

    public ControlItemDto toDto(Tweak t) {
        return toDto(t, t.isEnabled());
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

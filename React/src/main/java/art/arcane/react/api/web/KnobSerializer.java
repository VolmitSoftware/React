package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.util.project.config.ConfigDoc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class KnobSerializer {

    public List<KnobDto> knobs(Object configurable) {
        if (configurable == null) {
            return new ArrayList<>();
        }

        List<Field> fields = ConfigReflectionUtil.collectFields(configurable.getClass());

        List<KnobDto> result = new ArrayList<>();
        for (Field field : fields) {
            if (!field.isAnnotationPresent(ConfigDoc.class)) {
                continue;
            }
            if (field.getName().equals("enabled")) {
                continue;
            }

            Class<?> normalized = ConfigReflectionUtil.normalizeType(field.getType());
            String type = ConfigReflectionUtil.classifyType(normalized);
            if (type == null) {
                continue;
            }

            Object rawValue;
            try {
                field.setAccessible(true);
                rawValue = field.get(configurable);
            } catch (Throwable ignored) {
                continue;
            }

            ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);

            KnobDto knob = new KnobDto();
            knob.key = field.getName();
            knob.label = ConfigReflectionUtil.displayName(field.getName());
            knob.type = type;
            knob.doc = annotation.value();

            if (normalized.isEnum()) {
                knob.value = rawValue != null ? ((Enum<?>) rawValue).name() : null;
                Object[] constants = normalized.getEnumConstants();
                String[] options = constants == null ? new String[0] : new String[constants.length];
                if (constants != null) {
                    for (int i = 0; i < constants.length; i++) {
                        options[i] = ((Enum<?>) constants[i]).name();
                    }
                }
                knob.options = options;
            } else {
                knob.value = rawValue;
                knob.options = new String[0];
            }

            result.add(knob);
        }

        return result;
    }
}

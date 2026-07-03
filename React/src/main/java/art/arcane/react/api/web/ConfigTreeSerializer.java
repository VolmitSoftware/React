package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.project.config.ConfigDoc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigTreeSerializer {

    private static final Logger LOG = Logger.getLogger(ConfigTreeSerializer.class.getName());

    public ConfigSectionDto[] serialize(ReactConfiguration config) {
        List<ConfigSectionDto> sections = new ArrayList<>();
        List<ConfigNodeDto> generalNodes = new ArrayList<>();

        for (Field field : ConfigReflectionUtil.collectFields(config.getClass())) {
            Object value = getFieldValue(field, config);

            String type = ConfigReflectionUtil.classifyType(ConfigReflectionUtil.normalizeType(field.getType()));
            if (type != null) {
                generalNodes.add(buildNode("main." + field.getName(), field, value));
            } else if (isNestedConfigType(field.getType())) {
                ConfigSectionDto section = buildSection(field.getName(), "main." + field.getName(), value);
                if (section != null && section.nodes.length > 0) {
                    sections.add(section);
                }
            }
        }

        List<ConfigSectionDto> result = new ArrayList<>();
        if (!generalNodes.isEmpty()) {
            ConfigSectionDto general = new ConfigSectionDto();
            general.name = "general";
            general.nodes = generalNodes.toArray(new ConfigNodeDto[0]);
            result.add(general);
        }
        result.addAll(sections);
        return result.toArray(new ConfigSectionDto[0]);
    }

    private ConfigSectionDto buildSection(String sectionName, String pathPrefix, Object sectionObject) {
        if (sectionObject == null) {
            return null;
        }

        List<ConfigNodeDto> nodes = new ArrayList<>();
        for (Field field : ConfigReflectionUtil.collectFields(sectionObject.getClass())) {
            Object value = getFieldValue(field, sectionObject);
            String type = ConfigReflectionUtil.classifyType(ConfigReflectionUtil.normalizeType(field.getType()));
            if (type == null) {
                continue;
            }
            nodes.add(buildNode(pathPrefix + "." + field.getName(), field, value));
        }

        ConfigSectionDto section = new ConfigSectionDto();
        section.name = sectionName;
        section.nodes = nodes.toArray(new ConfigNodeDto[0]);
        return section;
    }

    private ConfigNodeDto buildNode(String key, Field field, Object rawValue) {
        Class<?> normalized = ConfigReflectionUtil.normalizeType(field.getType());
        String type = ConfigReflectionUtil.classifyType(normalized);

        ConfigNodeDto node = new ConfigNodeDto();
        node.key = key;
        node.label = ConfigReflectionUtil.displayName(field.getName());
        node.type = type;

        ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
        node.doc = annotation != null ? annotation.value() : "";

        if (normalized != null && normalized.isEnum()) {
            node.value = rawValue != null ? ((Enum<?>) rawValue).name() : null;
            Object[] constants = normalized.getEnumConstants();
            String[] options = constants == null ? new String[0] : new String[constants.length];
            if (constants != null) {
                for (int i = 0; i < constants.length; i++) {
                    options[i] = ((Enum<?>) constants[i]).name();
                }
            }
            node.options = options;
        } else {
            node.value = rawValue;
            node.options = new String[0];
        }

        return node;
    }

    private boolean isNestedConfigType(Class<?> type) {
        Class<?> normalized = ConfigReflectionUtil.normalizeType(type);
        if (normalized == null) {
            return false;
        }
        if (normalized.isPrimitive() || normalized.isEnum()) {
            return false;
        }
        if (normalized == String.class || normalized == Character.class || normalized == Boolean.class) {
            return false;
        }
        if (isNumericBoxed(normalized)) {
            return false;
        }
        if (Map.class.isAssignableFrom(normalized) || Collection.class.isAssignableFrom(normalized) || normalized.isArray()) {
            return false;
        }
        String packageName = normalized.getPackageName();
        return packageName != null && packageName.startsWith("art.arcane.react");
    }

    private static boolean isNumericBoxed(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Double.class
                || type == Float.class || type == Short.class || type == Byte.class;
    }

    private static Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable t) {
            LOG.fine("ConfigTreeSerializer: failed to read field " + field.getDeclaringClass().getSimpleName()
                    + "." + field.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }
}

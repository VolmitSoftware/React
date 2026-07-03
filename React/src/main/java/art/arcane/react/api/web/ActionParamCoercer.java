package art.arcane.react.api.web;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.core.gui.ReactConfigGUI;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

public class ActionParamCoercer {

    public ActionParams coerce(Action<?> action, Map<String, Object> values) {
        ActionParams params = action.getDefaultParams();
        if (params == null || values == null || values.isEmpty()) {
            return params;
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String fieldName = entry.getKey();
            Object rawValue = entry.getValue();
            Field field = findField(params.getClass(), fieldName);
            if (field == null) {
                continue;
            }
            ReactConfigGUI.ParseResult result = ReactConfigGUI.parseInputValue(
                field.getType(),
                String.valueOf(rawValue)
            );
            if (!result.success()) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(params, result.value());
            } catch (Throwable ignored) {
            }
        }

        return params;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                Field field = cursor.getDeclaredField(name);
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }
}

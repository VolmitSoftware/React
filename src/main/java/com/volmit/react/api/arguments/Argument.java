package com.volmit.react.api.arguments;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument
{
    String name();
    String shortCode();
    String description();
    Class<?> listType();

    public static class ArgumentProcessor {
        private static Object mapToType(Class<?> type, Class<?> listType, String input) throws Throwable {
            if(type.equals(List.class) || type.equals(Set.class)) {
                List<Object> objects = new ArrayList<>();
                for(String i : input.split(",")) {
                    try {
                        objects.add(mapToType(listType, null, i.trim()));
                    }

                    catch(Throwable e) {
                        throw new RuntimeException("Could not parse list type " + listType.getSimpleName() + " from " + i.trim());
                    }
                }

                return type.equals(List.class) ? objects : new HashSet<>(objects);
            }

            if(type.equals(int.class) || type.equals(Integer.class)) {
                return Integer.parseInt(input);
            }
            if(type.equals(double.class) || type.equals(Double.class)) {
                return Double.parseDouble(input);
            }
            if(type.equals(float.class) || type.equals(Float.class)) {
                return Float.parseFloat(input);
            }
            if(type.equals(long.class) || type.equals(Long.class)) {
                return Long.parseLong(input);
            }
            if(type.equals(short.class) || type.equals(Short.class)) {
                return Short.parseShort(input);
            }
            if(type.equals(byte.class) || type.equals(Byte.class)) {
                return Byte.parseByte(input);
            }
            if(type.equals(boolean.class) || type.equals(Boolean.class)) {
                return input.trim().equals("1")
                    || input.trim().equalsIgnoreCase("y")
                    || input.trim().equalsIgnoreCase("yes")
                    || input.trim().equalsIgnoreCase("on")
                    || Boolean.parseBoolean(input);
            }
            if(type.equals(String.class)) {
                return input;
            }
            if(type.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) type, input.toUpperCase());
            }

            throw new RuntimeException("Cannot parse type " + type.getName());
        }

        public static <T> T process(Class<T> clazz, String[] args) throws Exception {
            CursedComponent c = Curse.on(clazz).construct();
            List<CapturedArgument> arguments = c.instanceFields()
                .filter(i -> i.isAnnotated(Argument.class))
                .map((i) -> new CapturedArgument(i.annotated(Argument.class), i, c))
                .toList();
            StringBuilder valueBuffer = new StringBuilder();
            Map<String, String> valueMap = new HashMap<>();
            String currentKey = null;

            for(String i : args) {
                if(i.startsWith("-")) {
                    if(!valueBuffer.isEmpty() && currentKey != null) {
                        valueMap.put(currentKey, valueBuffer.toString().trim());
                    }

                    currentKey = i.substring(1);
                    valueBuffer = new StringBuilder();
                }

                else {
                    valueBuffer.append(i).append(" ");
                }
            }

            if(!valueBuffer.isEmpty() && currentKey != null) {
                valueMap.put(currentKey, valueBuffer.toString().trim());
            }

            for(String i : valueMap.keySet()) {
                for(CapturedArgument j : arguments) {
                    if(j.getArgument().name().equalsIgnoreCase(i) || j.getArgument().shortCode().equals(i)) {
                        try {
                            j.getField().set(mapToType(j.getField().field().getType(),
                                j.getArgument().listType(),
                                valueMap.get(i)));
                        } catch(Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            return c.instance();
        }

        @AllArgsConstructor
        @Data
        public static class CapturedArgument {
            private final Argument argument;
            private final CursedField field;
            private final CursedComponent component;
        }
    }
}

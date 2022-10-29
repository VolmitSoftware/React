package com.volmit.react.api.sampler;

import com.volmit.react.React;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// The name must be XReactSampler
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XReactSampler {
    // These are required for sampler registration
    String id();
    int interval() default 50;
    String suffix() default "";
}

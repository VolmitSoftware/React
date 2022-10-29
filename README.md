# React 8

## Make an API-less sampler

### Make sure you create an annotation `XReactSampler`

```java
// The name must be XReactSampler
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XReactSampler {
    // These are required for sampler registration
    String id();
    int interval() default 50;
    String suffix() default "";
}

```

This can be re-used for any samplers you choose to create.

### Define the sampler
```java
@XReactSampler(
    // The id of the sampler is used in configurations for server owners
    id = "some-sampler-id",
    // The interval is in milliseconds. This is how often the sampler will run
    interval = 50,
    // The suffix is used to display the value of the sampler
    suffix = "/s")
public class ExampleSampler {
    // React will call this at the interval you specify if the sampler is used
    // This method must be parallel capable & thread safe, as it will be called
    // from a different thread than the main thread
    public double sample() {
        return 0;
    }
}
```

React will automatically find your samplers and register / unregister them.
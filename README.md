# React

React is a runtime performance and monitoring plugin for current Paper, Purpur, and Folia servers. It provides server samplers, optional performance features and tweaks, operator actions, in-game monitors and maps, and public APIs for entity protection and metric publishing.

React also runs on Spigot with fewer features. Dynamic View Distance and Afk View Shedding need Paper or Purpur, and the features that patch server code (Lazy Gravity, Furnace Brew Batching, Explosion Packet Batching, Hopper Chain Coalescing) only measure when the JVM does not accept a bytecode agent. Spigot 26.3 did not accept one in testing.

The authoritative documentation begins with [`00 - Overview.md`](https://github.com/VolmitSoftware/docs/blob/master/react/00-overview.md). Installation and configuration are covered in [`01 - Installation & Configuration.md`](https://github.com/VolmitSoftware/docs/blob/master/react/01-installation-configuration.md), and the complete document index is maintained in [`React/AGENTS.md`](React/AGENTS.md).

## Build

React requires Java 25. Run the wrapper from `React/`:

```bash
./gradlew build
./gradlew test
./gradlew shadowJar
```

The shaded plugin jar is written to `React/build/libs/`.

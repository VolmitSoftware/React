# Changelog

## x.x.x

### Added

- Single-jar support for Paper 26.1.2 and 26.2: the v26_2_R1 NMS bridge now also activates on 26.1.2 servers (same Mojang-mapped module serves both).

### Changed

- Compile baseline lowered to paper-api 26.1.2.build.74-stable; plugin.yml api-version lowered to 26.1.
- Mob stacking resolves the cube-mob size API via Class.forName capability probe (AbstractCubeMob on 26.2+, Slime on 26.1.x) instead of the compile-time 26.2-only AbstractCubeMob type.
- Monitor publishes its header into the shared cooperative action-bar compositor, pinned to the center slot; other plugins' HUD text merges around it instead of fighting for the surface. Stopping the monitor clears only its own segment.

### Removed

- Monitor boss-bar fallback: losing the action bar no longer moves the monitor to a boss bar. Boss bars are reserved for Iris loaders fleet-wide.

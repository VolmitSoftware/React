# React
![Alt text](https://storage.googleapis.com/psycho_upload/ShareX/2023/08/Title_Card.png "React Title Card")


The master branch is for the latest version of minecraft.

## Language and localization

Canonical server English is defined in the typed Java catalogs under `React/src/main/java/art/arcane/react/localization`; canonical Reactor dashboard English is defined in `reactor/lib/localization`. Neither surface requires a duplicate English translation resource during feature work. Complete bundles are included for German, Spanish, Finnish, French, Hebrew, Italian, Japanese, Korean, Lithuanian, Dutch, Polish, Portuguese, Russian, Turkish, Vietnamese, Simplified Chinese, and Traditional Chinese. React's `language` setting selects the server locale, and a TOML file at `languages/overrides/<locale>.toml` can override only selected server messages. Missing entries resolve from the selected bundle and then code-owned English.

## PlaceholderAPI

React registers the `react` expansion when PlaceholderAPI is installed. Keys are `%react_<path>%`, where the path is one to four dot-separated segments of lowercase letters, digits and hyphens. There is no underscore anywhere in a path — the underscore is PlaceholderAPI's own identifier terminator.

Resolution never touches Bukkit, never takes a lock and never allocates. A React-owned publisher builds one immutable snapshot per second on React's existing ticker; reading a placeholder is one volatile read plus a hash lookup.

Three return values, three meanings:

| Rendered | Meaning |
|---|---|
| `%react_mspt-p59%` (the literal) | the path is not a React key — a typo |
| `---` | a real key with no value right now |
| `0` | genuinely zero |

### Keys

| Key | Value | Backing sampler |
|---|---|---|
| `%react_available%` | `true` once a snapshot has been published, otherwise `false` | — |
| `%react_tps%` | ticks per second, 2 decimals | `ticks-per-second` |
| `%react_mspt%` | mean tick time in milliseconds, 2 decimals | `tick-time` |
| `%react_mspt-p95%` | 95th-percentile tick time in milliseconds, 2 decimals | `tick-ms-p95` |
| `%react_health%` | 0-100, higher is better; the inverse of React's incident score, clamped | `incident-score` |
| `%react_top-world-mspt%` | milliseconds attributed to the most expensive world, 2 decimals | `top-world-mspt` |
| `%react_entities%` | entity count, exact integer | `entities` |
| `%react_chunks%` | loaded chunk count, exact integer | `chunks` |
| `%react_ground-items%` | dropped item count, exact integer | `ground-items` |
| `%react_memory.used%` | used heap in whole megabytes | `memory-used` |
| `%react_memory.free%` | free heap in whole megabytes | `memory-free` |
| `%react_world.mspt%` | milliseconds attributed to the world the reading player is in, 2 decimals | `per-world-tick-time` |
| `%react_sampler.<id>%` | any registered sampler by id | that sampler |

`%react_sampler.<id>%` is the whole sampler registry, 147 built-in ids plus the per-plugin cost samplers and any third-party metrics registered at runtime, including every cross-plugin metric React mirrors — `%react_sampler.iris-pregen-queue%`, `%react_sampler.adapt-xp-rate%`, `%react_sampler.wormholes-portals%`, `%react_sampler.tick-ms-p99%`, `%react_sampler.gc-time-percent%`. Run `/papi info react` for the live list. A mirrored metric answers `---` while its owning plugin is absent, and lags the owning plugin's own expansion by its publish interval plus about two seconds; where a plugin publishes its own key, that key is canonical and React's is the fallback.

### Value format

Counts are exact integers. Rates and fractions carry exactly two decimals with `.` as the separator. No grouping separators, no unit suffixes, no percent sign and no colour codes ever appear in a value — a `%` in a value would open a new placeholder in any consumer that runs a second substitution pass. Put units in your own template.

### Cost

React only samples the metrics a placeholder has actually asked for, so a scoreboard naming four keys costs four samples per second, not 147, and leaves React's sleep-when-unobserved idle savings intact for everything else. A key becomes live within about two seconds of its first use.

# [Support](https://discord.gg/3xxPTpT) **|** [Documentation](https://docs.volmit.com/react/)

# Building

Building React is fairly simple, though you will need to setup a few things if your system has never been used for java
development.

Consider supporting our development by buying React on spigot! We work hard to make React the best it can be for everyone.

## Preface: if you need help compiling and you are a developer / intend to help out in the community or with development we would love to help you regardless in the discord! however do not come to the discord asking for free copies, or a tutorial on how to compile.

### Command Line Builds

1. Install [Java JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-17.0.1` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 17
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory
3. If this is your first time building React for MC 1.18+ run `gradlew setup` inside the root React project folder.
   Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
4. Once the project has setup, run `gradlew React`
5. The React jar will be placed in `React/build/React-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!

### IDE Builds (for development)

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 17 (in settings, search for gradle)
* Add a build line in the build.gradle for your own build task to directly compile React into your plugins folder if you
  prefer.
* Resync the project & run your newly created task (under the development folder in gradle tasks!)

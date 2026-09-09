package art.arcane.react.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.TextValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

final class ReactLanguageReference {
  private ReactLanguageReference() {
  }

  static String englishCatalog() {
    StringBuilder output = new StringBuilder();
    for (String line : header(ReactMessages.catalog().englishLocale())) {
      output.append(line.isEmpty() ? "#" : "# " + line).append('\n');
    }
    JsonObject messages = new JsonObject();
    for (MessageKey key : ReactMessages.catalog().keys()) {
      MessageValue value = key.englishValue();
      if (value instanceof TextValue text) {
        messages.addProperty(key.id(), text.template());
      } else if (value instanceof LinesValue lines) {
        JsonArray array = new JsonArray();
        for (String line : lines.lines()) {
          array.add(line);
        }
        messages.add(key.id(), array);
      } else {
        throw new IllegalArgumentException("Unsupported React language value: " + key.id());
      }
    }
    return output.append('\n').append(TomlCodec.toToml(messages)).toString();
  }

  static List<String> header(String locale) {
    return List.of(
        "React language: " + locale,
        "",
        "Edit this file directly in the languages folder or through /react language server edit.",
        "React creates or downloads language files only when missing. Local changes are preserved.",
        "Missing messages use the built-in English catalog.",
        "",
        "Formatting",
        "  Chat messages use MiniMessage: <red>text</red>, <bold>text</bold>.",
        "  RGB colors use <color:#RRGGBB>text</color>. Close tags in reverse opening order.",
        "  Shared language.* editor messages also support classic ampersand color codes such as &e and &r.",
        "  Other messages do not convert ampersand color codes. Keep TOML quoting and escaping valid.",
        "  Use \\n inside quoted TOML strings for a new line. Double braces {{ and }} print literal braces.",
        "  renderer.* and config.documentation.annotation.* values are plain text.",
        "  test.* values are plain text except test.result.* messages, which use MiniMessage.",
        "",
        "Placeholders",
        "Each message accepts only its own placeholders. Keep their names unchanged.",
        "Use placeholders in message text, outside MiniMessage tags.",
        "  {action}          Action name or color adjustment direction",
        "  {actions}         Queued action count or list of excluded action names",
        "  {active}          Number of active features or tweaks",
        "  {actor}           Web user who made a change",
        "  {added}           Whether a feature rejoined the active registry after activation",
        "  {after}           Configuration value, language message, or heap usage after a change",
        "  {age}             Age of integration data in seconds",
        "  {amount}          Number of items to give",
        "  {architecture}    CPU architecture",
        "  {argument}        Unexpected command argument",
        "  {available}       Number of available NMS bridge handles",
        "  {average}         Average TPS or milliseconds per tick",
        "  {avg_mspt}        Average milliseconds per tick during a load-test pass",
        "  {avg_tps}         Average ticks per second during a load-test pass",
        "  {bar}             Formatted benchmark score bar",
        "  {before}          Configuration value, language message, or heap usage before a change",
        "  {checked}         Number of samplers checked",
        "  {chunks}          Number of chunks affected by an action",
        "  {code}            Web pairing code",
        "  {command}         Command path",
        "  {count}           Number of entries, modules, changes, or test findings",
        "  {current}         Current page number",
        "  {delta_heap}      Difference in ending heap usage between React and baseline, in MB",
        "  {delta_mspt}      Difference in average tick time between React and baseline, in ms",
        "  {delta_tps}       Difference in average TPS between React and baseline",
        "  {detail}          Bridge, integration, action, or test diagnostic details",
        "  {dimensions}      Detected map-wall grid dimensions",
        "  {direction}       Direction to rotate the selected color's hue",
        "  {distance}        Requested view or simulation distance",
        "  {duration}        Elapsed time; load-test start messages use seconds",
        "  {enabled}         Enabled state or number of enabled modules",
        "  {entities}        Number of entities affected by an action",
        "  {entry}           Integration timeline entry",
        "  {exceptions}      React exceptions recorded during a load-test pass",
        "  {fail}            Number of failed checks",
        "  {file}            Configuration, language, or plugin API file name",
        "  {fingerprint}     Server or web token fingerprint",
        "  {finite}          Number of samplers returning finite values",
        "  {frames}          Number of item frames in the active map wall",
        "  {free}            Free physical or virtual memory",
        "  {freed}           Heap memory reclaimed by garbage collection",
        "  {from}            First entry number on the current page",
        "  {grid}            Map-wall grid dimensions",
        "  {ground_y}        Ground height for the gravity test",
        "  {group}           Monitor, sampler, map, configuration, or language-message group",
        "  {growth}          Heap growth during a load test, in MB",
        "  {health}          Integration health state",
        "  {heap_end}        Heap usage at the end of a load-test pass, in MB",
        "  {heap_start}      Heap usage at the start of a load-test pass, in MB",
        "  {heartbeat}       Time since the integration's latest heartbeat",
        "  {height}          Test canvas height in pixels",
        "  {hoppers}         Number of hoppers normalized",
        "  {id}              Bridge handle, sampler, map, action, pack, or token identifier",
        "  {impact}          Configuration effect description or plugin cost in ms/s",
        "  {incident}        Identifier of the recursive incident action excluded from the dev suite",
        "  {index}           Current dev-suite step number",
        "  {invalid}         Number of samplers with invalid readings",
        "  {issued_at}       Web token creation time",
        "  {item}            Item name or identifier",
        "  {items}           Number of item entities merged",
        "  {key}             Command parameter key, configuration key, or language message ID",
        "  {label}           Benchmark, metric, setting, command, or web token label",
        "  {line}            Line number being edited in a multi-line message",
        "  {lines}           Number of server log lines scanned",
        "  {loaded}          Number of chunks newly loaded during prewarming",
        "  {locale}          Language code being displayed, selected, or edited",
        "  {matched}         Worlds whose autosave state matches the test baseline",
        "  {max_tick}        Longest observed tick gap during a load test, in ms",
        "  {maximum}         Maximum item count, tick-time threshold, or editor input length",
        "  {message}         Completed action summary or integration status message",
        "  {metrics}         Number of metrics supplied by a plugin API pack",
        "  {minecraft}       Minecraft version",
        "  {minimum}         Minimum acceptable TPS",
        "  {missing}         Number of required samplers missing from the registry",
        "  {mode}            Selected game mode or ability mode",
        "  {model}           CPU model",
        "  {name}            Benchmark, module, renderer, or test check name",
        "  {note}            Reason the server log was not scanned",
        "  {offenders}       Number of samplers returning non-finite values",
        "  {operations}      Ability callback operations per minute",
        "  {options}         Accepted configuration values",
        "  {p95}             95th-percentile tick time during a load test, in ms",
        "  {parameter}       Command parameter name",
        "  {parameters}      Command parameters that were ignored",
        "  {pass}            Number of passed checks",
        "  {path}            Configuration key path or report file path",
        "  {percent}         Color adjustment percentage or plugin impact share",
        "  {permission}      Required permission node",
        "  {personal}        Player's selected language code",
        "  {phase}           Feature lifecycle phase being tested",
        "  {pixels}          Number of non-blank pixels rendered by a map test",
        "  {platform}        Operating system or Minecraft server platform",
        "  {players}         Number of synthetic players in a load test",
        "  {plugin}          Plugin name",
        "  {process_load}    CPU load of the server process",
        "  {processors}      Number of available CPU processors",
        "  {profile}         React configuration profile name",
        "  {protocol}        Integration protocol version",
        "  {radius}          Chunk radius for a map or dev-suite action",
        "  {range}           Accepted view or simulation distance range",
        "  {rate}            Configured crop fast-forward rate",
        "  {rating}          Formatted benchmark rating",
        "  {react_errors}    Number of React error lines found in the server log",
        "  {reason}          Failure, rejection, or unavailable-state explanation",
        "  {registered}      Number of registered modules",
        "  {removed_first}   Whether initial deactivation removed a feature from the active registry",
        "  {removed_second}  Whether repeated deactivation removed a feature from the active registry",
        "  {renderer}        Map renderer name or identifier",
        "  {renderers}       List of map renderers with blank identifiers",
        "  {role}            Web access role: viewer, operator, or admin",
        "  {sampler}         Sampler name or identifier",
        "  {samplers}        List of integration or failing samplers",
        "  {scope}           Map scope or distance-setting scope",
        "  {score}           Benchmark score",
        "  {seconds}         Countdown or load-test duration in seconds",
        "  {section}         Configuration section name",
        "  {server}          Minecraft server implementation and version",
        "  {shown}           Number of rows currently displayed",
        "  {since_run}       Errors reported since the current test started",
        "  {size}            Maximum or active map-wall dimensions",
        "  {skip}            Number of skipped checks",
        "  {spawn_y}         Height where the gravity-test block was spawned",
        "  {state}           Action or plugin API pack state",
        "  {status}          Integration status or configuration state label",
        "  {subject}         Module or setting described by configuration help",
        "  {subsystem}       Test subsystem name",
        "  {summary}         Action audit summary",
        "  {system_load}     CPU load of the whole system",
        "  {target}          World, player, plugin, setting, or plugin group targeted by an operation",
        "  {threading}       Server threading mode",
        "  {tick}            Tick duration that exceeded the freeze threshold, in ms",
        "  {tick_crashes}    Number of tick-crash lines found in the server log",
        "  {tier}            Incident playbook severity tier",
        "  {timing}          Ability callback time in milliseconds per minute",
        "  {to}              Last entry number on the current page",
        "  {total}           Total entries, modules, worlds, checks, or memory in the message",
        "  {type}            Command parameter, configuration value, map, or distance type",
        "  {unavailable}     Number of unavailable NMS bridge handles",
        "  {unit}            Unit label for a pie-chart total",
        "  {unknown}         Identifier of the unknown action excluded from the dev suite",
        "  {unreadable}      Number of samplers that could not be read",
        "  {uptime}          Host operating-system uptime",
        "  {url}             Uploaded diagnostic report URL",
        "  {usage}           Command usage syntax",
        "  {used}            Used physical memory, virtual memory, or heap",
        "  {value}           Formatted setting, measurement, or hardware detail",
        "  {values}          Sampled values listed in a dev-suite report",
        "  {variables}       Placeholder names accepted by the message being edited",
        "  {vendor}          Java vendor",
        "  {verdict}         Overall test verdict",
        "  {version}         React, Java, or platform version",
        "  {warmed}          Number of chunks prewarmed",
        "  {warn}            Number of checks with warnings",
        "  {width}           Test canvas width in pixels",
        "  {world}           Minecraft world name"
    );
  }
}

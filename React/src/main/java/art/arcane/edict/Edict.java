package art.arcane.edict;

import art.arcane.curse.Curse;
import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.endpoint.EdictEndpoint;
import art.arcane.edict.api.field.EdictField;
import art.arcane.edict.api.input.EdictInput;
import art.arcane.edict.api.input.MappedEdictInputHolder;
import art.arcane.edict.api.input.MappedInput;
import art.arcane.edict.api.input.PositionalEdictInputHolder;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;
import art.arcane.edict.api.request.EdictFieldResponse;
import art.arcane.edict.api.request.EdictRequest;
import art.arcane.edict.api.request.EdictResponse;
import com.volmit.react.api.action.ReactAction;
import lombok.Builder;
import lombok.Singular;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Builder
public class Edict {
    @Singular
    private final List<EdictEndpoint> endpoints = new ArrayList<>();

    @Singular
    private final Set<EdictParser<?>> parsers = defaultParsers();

    public Edict() {
        parsers.addAll(defaultParsers());
    }

    public Stream<EdictResponse> streamResponses(EdictRequest request){
        return endpoints.stream().map(i -> new EdictRequest(request).pathMatch(i))
            .filter(Objects::nonNull)
            .map(i -> respond(i, i.getPair()));
    }

    public Optional<EdictResponse> respond(EdictRequest request) {
        return streamResponses(request).min(Comparator.comparingInt(EdictResponse::getScoreOffset));
    }

    public EdictResponse respond(EdictRequest request, EdictEndpoint endpoint) {
        List<EdictInput> inputs = new ArrayList<>(request.getInputs());
        List<EdictFieldResponse> responseFields = new ArrayList<>();

        for(EdictField i : endpoint.getFields()) {
            List<EdictFieldResponse> fields = new ArrayList<>();

            if(inputs.isEmpty()) {
                responseFields.add(EdictFieldResponse.builder()
                    .field(i)
                    .inputPosition(-1)
                    .confidence(Confidence.INVALID)
                    .build());
            }

            for(int j = 0; j < inputs.size(); j++) {
                EdictInput input = inputs.get(j);
                AtomicReference<Confidence> confidence = new AtomicReference<>(Confidence.INVALID);
                fields.add(EdictFieldResponse.builder()
                    .value(input.into(Confidence.HIGH)
                        .map(k -> {
                            confidence.set(Confidence.HIGH);
                            return k;
                        }).or(() -> input.into(Confidence.LOW).map(k -> {
                            confidence.set(Confidence.LOW);
                            return k;
                        })).or(() -> input.into(Confidence.INVALID).map(k -> {
                            confidence.set(Confidence.INVALID);
                            return k;
                        })).orElse(null))
                    .field(i)
                    .nameDrift(input instanceof MappedInput ? i.getNames().stream().mapToInt(k -> k.compareToIgnoreCase(((MappedInput) input).getKey())).min().orElse(j) : j)
                    .inputPosition(j)
                    .confidence(confidence.get())
                    .build());
            }

            fields.stream().min(Comparator.comparingInt(a -> Confidence.values().length - ((EdictFieldResponse) a).getConfidence().ordinal())
                .thenComparingInt((a) -> ((EdictFieldResponse) a).getNameDrift()))
                .map(a -> {
                    inputs.remove(a.getInputPosition());
                    return a;
                }).ifPresentOrElse(responseFields::add, () -> responseFields.add(EdictFieldResponse.builder()
                    .field(i)
                    .inputPosition(-1)
                    .confidence(Confidence.INVALID)
                    .build()));
        }

        return EdictResponse.builder()
            .request(request)
            .endpoint(endpoint)
            .matchScore(request.getMatchOffset())
            .fields(responseFields)
            .build();
    }

    public EdictRequest request(String args) {
        return EdictRequest.of(this, args);
    }

    public EdictRequest request(String[] args) {
        return EdictRequest.of(this, args);
    }

    public EdictRequest request(List<String> args) {
        return EdictRequest.of(this, args);
    }

    public EdictInput parseInput(String var, int positionalIndex, int realIndex) {
        if(var.contains("=")) {
            String key = var.split("\\Q=\\E")[0];
            var = var.split("\\Q=\\E")[1];
            return new MappedEdictInputHolder(key, parse(var));
        }

        else {
            return new PositionalEdictInputHolder(positionalIndex, realIndex, parse(var));
        }
    }

    public List<EdictValue<?>> parse(String value) {
        List<EdictValue<?>> values = new ArrayList<>();
        for(EdictParser<?> i : parsers) {
            values.add(i.parse(value));
        }

        return values;
    }

    private static Set<EdictParser<?>> defaultParsers() {
        Set<EdictParser<?>> parsers = new HashSet<>();
        Curse.implemented(Edict.class, EdictParser.class)
            .forEach(parser -> parsers.add((EdictParser<?>) parser.construct()));

        return parsers;
    }


    public static List<String> enhance(List<String> args) {
        return enhanceMappings(enhanceQuotes(args, true));
    }

    public static List<String> enhanceQuotes(List<String> args) {
        return enhanceQuotes(args, true);
    }

    public static List<String> enhanceQuotes(List<String> args, boolean trim) {
        List<String> a = new ArrayList<>();

        if (args.isEmpty()) {
            return a;
        }

        StringBuilder flat = new StringBuilder();
        for (String i : args) {
            if (trim) {
                if (i.trim().isEmpty()) {
                    continue;
                }

                flat.append(" ").append(i.trim());
            } else {
                if (i.endsWith(" ")) {
                    flat.append(" ").append(i.trim()).append(" ");
                }
            }
        }

        flat = new StringBuilder(flat.length() > 0 ? trim ? flat.toString().trim().length() > 0
            ? flat.substring(1).trim()
            : flat.toString().trim() : flat.substring(1) : flat);
        StringBuilder arg = new StringBuilder();
        boolean quoting = false;

        for (int x = 0; x < flat.length(); x++) {
            char i = flat.charAt(x);
            char j = x < flat.length() - 1 ? flat.charAt(x + 1) : i;
            boolean hasNext = x < flat.length();

            if (i == ' ' && !quoting) {
                if (!arg.toString().trim().isEmpty() && trim) {
                    a.add(arg.toString().trim());
                    arg = new StringBuilder();
                }
            } else if (i == '"') {
                if (!quoting && (arg.length() == 0)) {
                    quoting = true;
                } else if (quoting) {
                    quoting = false;

                    if (hasNext && j == ' ') {
                        if (!arg.toString().trim().isEmpty() && trim) {
                            a.add(arg.toString().trim());
                            arg = new StringBuilder();
                        }
                    } else if (!hasNext) {
                        if (!arg.toString().trim().isEmpty() && trim) {
                            a.add(arg.toString().trim());
                            arg = new StringBuilder();
                        }
                    }
                }
            } else {
                arg.append(i);
            }
        }

        if (!arg.toString().trim().isEmpty() && trim) {
            a.add(arg.toString().trim());
        }

        return a;
    }

    public static List<String> enhanceMappings(List<String> args) {
        List<String> newArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--")) {
                if (i + 1 < args.size()) {
                    newArgs.add(arg.substring(2) + "=" + args.get(i + 1));
                    i++;
                }
            } else if (arg.startsWith("-")) {
                if (i + 1 < args.size() && !args.get(i + 1).startsWith("-")) {
                    newArgs.add(arg.substring(1) + "=" + args.get(i + 1));
                    i++;
                } else {
                    newArgs.add(arg.substring(1) + "=true");
                }
            } else if (arg.contains(":")) {
                newArgs.add(arg.replace(":", "="));
            } else {
                newArgs.add(arg);
            }
        }

        return newArgs;
    }

    public void register(EdictEndpoint... endpoints) {
        this.endpoints.addAll(Arrays.asList(endpoints));
    }

    public static void test(){
//        Edict edict = new Edict();
//        edict.register(EdictEndpoint.builder()
//                .command("react monitor")
//                .field(EdictField.builder().name("configure").name("edit").type(Boolean.class).defaults(false).build())
//            .build());
//
//        for(ReactAction i : actions) {
//            edict.register(EdictEndpoint.builder()
//                .command("react action " + i.getName())
//                .fields(i.getCommandFields())
//                .build());
//        }
    }
}

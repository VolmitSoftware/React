package art.arcane.edict;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.curse.Curse;
import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.context.EdictContext;
import art.arcane.edict.api.context.EdictContextResolver;
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
import com.volmit.react.React;
import com.volmit.react.util.format.Form;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Builder
public class Edict {
    @Builder.Default
    private final List<String> parserPackages = new ArrayList<>();

    @Builder.Default
    private final List<String> contextResolverPackages = new ArrayList<>();

    @Singular
    private final List<EdictEndpoint> endpoints = new ArrayList<>();

    @Singular
    private final Set<EdictContextResolver<?>> contextResolvers = defaultContextResolvers();

    @Singular
    private final Set<EdictParser<?>> parsers = defaultParsers();

    /**
     * Creates a new instance of edict. From here you can register commands by calling the register method
     */
    public Edict() {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        parsers.addAll(defaultParsers(parserPackages));
        contextResolvers.addAll(defaultContextResolvers(contextResolverPackages));
        React.verbose("Edict Endpoints: " + endpoints.size());
        React.verbose("Edict Parsers: " + parsers.size());
        React.verbose("Edict Context Resolvers: " + contextResolvers.size());
        React.verbose("Edict Initialized in " + Form.f(p.getMilliseconds(), 2) + "ms");
    }

    /**
     * This method splits a string into a list of arguments. It divides the input string on spaces
     * and removes any empty strings from the result.
     *
     * @param args The string to be split into arguments. This should be a space-separated string.
     * @return A list of strings, each of which is a non-empty argument from the input string.
     */
    public static List<String> toArgs(String args) {
        return Arrays.stream(args.split("\\Q \\E")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public static String getEdictRootPackage() {
        return Edict.class.getPackageName();
    }

    public static String getEdictPackage(String sub) {
        if(!sub.startsWith(".")) {
            sub = "." + sub;
        }

        return getEdictRootPackage() + sub;
    }

    /**
     * This method processes an EdictRequest and generates a Stream of EdictResponses. Each response is based on one of the
     * available endpoints. The request is mapped to each endpoint, and if the mapping is successful, a response is generated.
     * The responses are streamed, meaning they are generated and processed one at a time, which can be more efficient for
     * large numbers of endpoints.
     *
     * @param request The EdictRequest object containing the inputs to be mapped.
     * @return A Stream of EdictResponse objects. Each response corresponds to a different endpoint and contains information
     *         about how the inputs from the request map to the fields in the endpoint.
     */
    public Stream<EdictResponse> streamResponses(EdictRequest request){
        return endpoints.stream().map(i -> new EdictRequest(request).pathMatch(i))
            .filter(Objects::nonNull)
            .map(i -> respond(i, i.getPair()));
    }

    public Optional<EdictResponse> execute(CommandSender sender, String command) {
        return respond(request(command)).map(i -> {
            EdictContext c = EdictContext.builder()
                .sender(sender)
                .response(i)
                .endpoint(i.getEndpoint())
                .request(i.getRequest())
                .build();
            EdictContext.put(c);
            i.getEndpoint().getExecutor().accept(c);
            return i;
        });
    }

    public Optional<EdictResponse> respond(EdictRequest request) {
        return streamResponses(request)
            .peek((i) -> React.verbose("Request on " + i.getEndpoint().getCommand() + ": " + i.getScoreOffset()))
            .min(Comparator.comparingInt((i) -> Math.abs(i.getScoreOffset())));
    }

    /**
     * This method processes an EdictRequest and generates an EdictResponse based on the provided EdictEndpoint.
     * It maps the inputs of the request to the fields in the endpoint, then generates an EdictFieldResponse for each field.
     * The EdictFieldResponses are generated based on the confidence level of the match between the input and the field.
     * The input is removed from the input list once a match is found.
     * If no input matches a field, an EdictFieldResponse with INVALID confidence is added.
     *
     * @param request The EdictRequest object containing the inputs to be mapped.
     * @param endpoint The EdictEndpoint object containing the fields to which the inputs are to be mapped.
     * @return An EdictResponse object containing the original request, the endpoint, a match score,
     *         and a list of EdictFieldResponses. Each EdictFieldResponse corresponds to a field in the endpoint,
     *         and contains information about the matching input and the confidence level of the match.
     */
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
                    .nameDrift(input instanceof MappedInput ? i.getNames()
                        .stream()
                        .mapToInt(k -> calculateLevenshteinDistance(k, ((MappedInput) input).getKey()))
                        .min()
                        .orElse(j) : j)
                    .inputPosition(j)
                    .confidence(confidence.get())
                    .build());
            }

            fields.stream().min(Comparator.comparingInt(a -> Math.abs(Confidence.values().length - ((EdictFieldResponse) a).getConfidence().ordinal()))
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

    /**
     * This method generates an EdictRequest object from a string of arguments. The string is split into individual
     * arguments based on spaces.
     *
     * @param args A string of space-separated arguments to be included in the request.
     * @return An EdictRequest object with the arguments split from the input string.
     */
    public EdictRequest request(String args) {
        return EdictRequest.of(this, args);
    }

    /**
     * This method generates an EdictRequest object from an array of arguments.
     *
     * @param args An array of arguments to be included in the request.
     * @return An EdictRequest object with the arguments taken from the input array.
     */
    public EdictRequest request(String[] args) {
        return EdictRequest.of(this, args);
    }

    /**
     * This method generates an EdictRequest object from a list of arguments.
     *
     * @param args A list of arguments to be included in the request.
     * @return An EdictRequest object with the arguments taken from the input list.
     */
    public EdictRequest request(List<String> args) {
        return EdictRequest.of(this, args);
    }

    /**
     * Parses the given input string into an EdictInput. If the string contains an "=" character, it is split into a key and
     * a value, and a MappedEdictInputHolder is returned. If not, a PositionalEdictInputHolder is returned, which holds
     * the original string and the positional index.
     *
     * @param var The input string to be parsed into an EdictInput. If it contains "=", it is treated as a key-value pair.
     * @param positionalIndex The position of the input string in the original sequence of arguments.
     *        This is used when creating a PositionalEdictInputHolder.
     * @param realIndex The index of the input in the overall sequence, considering all inputs, not just the positional ones.
     *        This is also used when creating a PositionalEdictInputHolder.
     * @return A new EdictInput, which can be either a MappedEdictInputHolder if the input string contains "=", or
     *         a PositionalEdictInputHolder otherwise.
     */
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

    /**
     * Parses the given input string into a list of EdictValue objects. Each object in the list is generated
     * by one of the parsers available in the parsers list. This means the input string is parsed multiple times,
     * once for each parser.
     *
     * @param value The input string to be parsed into EdictValue objects.
     * @return A list of EdictValue objects. Each object represents a possible parsing of the input string,
     *         based on one of the available parsers.
     */
    public List<EdictValue<?>> parse(String value) {
        List<EdictValue<?>> values = new ArrayList<>();
        for(EdictParser<?> i : parsers) {
            values.add(i.parse(value));
        }

        return values;
    }

    /**
     * This method creates a set of default parsers by dynamically instantiating all implementations of
     * the EdictParser interface found in the "art.arcane.edict.content.parser" package. These parsers are
     * typically used to parse input strings into EdictValue objects. This method uses reflection to discover
     * and instantiate the parsers, hence any failure in the instantiation process is silently ignored.
     *
     * @return A set of EdictParser objects, each one an instance of a different implementation of the
     *         EdictParser interface found in the "art.arcane.edict.content.parser" package.
     */
    public static Set<EdictParser<?>> defaultParsers() {
        return defaultParsers(null);
    }

    /**
     * This method creates a set of default parsers by dynamically instantiating all implementations of
     * the EdictParser interface found in the "art.arcane.edict.content.parser" package. These parsers are
     * typically used to parse input strings into EdictValue objects. This method uses reflection to discover
     * and instantiate the parsers, hence any failure in the instantiation process is silently ignored.
     *
     * @param and A list of additional packages to for more parsers.
     *
     * @return A set of EdictParser objects, each one an instance of a different implementation of the
     *         EdictParser interface found in the "art.arcane.edict.content.parser" package.
     */
    public static Set<EdictParser<?>> defaultParsers(List<String> and) {
        Set<EdictParser<?>> parsers = new HashSet<>();
        Curse.implementedInPackage(Edict.class, EdictParser.class, Edict.getEdictPackage("content.parser"))
            .forEach(parser -> {
                try {
                    parsers.add(parser.construct().instance());
                }

                catch(Throwable ignored) {

                }
            });

        if(and != null) {
            for(String i : and) {
                Curse.implementedInPackage(Edict.class, EdictParser.class, i)
                    .forEach(parser -> {
                        try {
                            parsers.add(parser.construct().instance());
                        }

                        catch(Throwable ignored) {

                        }
                    });
            }
        }

        return parsers;
    }

    /**
     * This method creates a set of default context resolvers by dynamically instantiating all implementations of
     * the EdictParser interface found in the "art.arcane.edict.content.context" package. These resolvers are
     * typically used to resolve what objects are related to the sender. This method uses reflection to discover
     * and instantiate the parsers, hence any failure in the instantiation process is silently ignored.
     *
     * @return A set of EdictContextResolver objects, each one an instance of a different implementation of the
     *         EdictContextResolver interface found in the "art.arcane.edict.content.context" package.
     */
    public static Set<EdictContextResolver<?>> defaultContextResolvers() {
        return defaultContextResolvers(null);
    }

    /**
     * This method creates a set of default context resolvers by dynamically instantiating all implementations of
     * the EdictParser interface found in the "art.arcane.edict.content.context" package. These resolvers are
     * typically used to resolve what objects are related to the sender. This method uses reflection to discover
     * and instantiate the parsers, hence any failure in the instantiation process is silently ignored.
     *
     * @param and A list of additional packages to search for more context resolvers.
     *
     * @return A set of EdictContextResolver objects, each one an instance of a different implementation of the
     *         EdictContextResolver interface found in the "art.arcane.edict.content.context" package.
     */
    public static Set<EdictContextResolver<?>> defaultContextResolvers(List<String> and) {
        Set<EdictContextResolver<?>> resolvers = new HashSet<>();
        Curse.implementedInPackage(Edict.class, EdictContextResolver.class, Edict.getEdictPackage("content.context"))
            .forEach(parser -> {
                try {
                    resolvers.add(parser.construct().instance());
                }

                catch(Throwable ignored) {

                }
            });

        if(and != null) {
            for(String i : and) {
                Curse.implementedInPackage(Edict.class, EdictContextResolver.class, i)
                    .forEach(parser -> {
                        try {
                            resolvers.add(parser.construct().instance());
                        }

                        catch(Throwable ignored) {

                        }
                    });
            }
        }

        return resolvers;
    }

    /**
     * Enhances the provided list of arguments by applying a series of transformations, specifically:
     * processing quotes and enhancing mappings.
     *
     * @param args The list of arguments to be enhanced.
     * @return A new list of enhanced arguments.
     */
    public static List<String> enhance(List<String> args) {
        return enhanceMappings(enhanceQuotes(args, true));
    }

    /**
     * Enhances the provided list of arguments by processing quotes and optionally trimming whitespace.
     *
     * @param args The list of arguments to be enhanced.
     * @return A new list of enhanced arguments.
     */
    public static List<String> enhanceQuotes(List<String> args) {
        return enhanceQuotes(args, true);
    }

    /**
     * Enhances the provided list of arguments by processing quotes, with the option to trim whitespace.
     *
     * @param args The list of arguments to be enhanced.
     * @param trim A flag to indicate if whitespace should be trimmed.
     * @return A new list of enhanced arguments.
     */
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

    /**
     * Enhances the provided list of arguments by transforming argument mappings.
     *
     * @param args The list of arguments to be enhanced.
     * @return A new list of enhanced arguments.
     */
    public static List<String> enhanceMappings(List<String> args) {
        List<String> newArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if(arg.startsWith("--") && arg.contains("=")) {
                newArgs.add(arg.substring(2));
            } else if(arg.startsWith("-") && arg.contains("=")) {
                newArgs.add(arg.substring(1));
            } else if (arg.startsWith("--")) {
                if (i + 1 < args.size() && !args.get(i + 1).startsWith("-")) {
                    newArgs.add(arg.substring(2) + "=" + args.get(i + 1));
                    i++;
                } else {
                    newArgs.add(arg.substring(2) + "=true");
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

    /**
     * Calculates the Levenshtein distance between two strings, which is a measure of the minimum number of
     * single-character edits (insertions, deletions, or substitutions) needed to change one string into the other.
     *
     * @param x The first string to be compared.
     * @param y The second string to be compared.
     * @return The Levenshtein distance between the two strings.
     */
    public static int calculateLevenshteinDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                }
                else if (j == 0) {
                    dp[i][j] = i;
                }
                else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1]
                                + costOfSubstitution(x.charAt(i - 1), y.charAt(j - 1)),
                            dp[i - 1][j] + 1),
                        dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }

    /**
     * Calculates the cost of substituting one character for another in a string. The cost is zero if the characters are
     * the same, and one otherwise.
     *
     * @param a The first character.
     * @param b The second character.
     * @return The substitution cost, as an integer.
     */
    public static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 1;
    }

    /**
     * Registers one or more endpoints to this instance.
     *
     * @param endpoints The endpoints to be registered.
     */
    public void register(EdictEndpoint... endpoints) {
        this.endpoints.addAll(Arrays.asList(endpoints));
    }
}

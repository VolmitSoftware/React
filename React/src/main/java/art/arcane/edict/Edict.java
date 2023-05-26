package art.arcane.edict;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedMethod;
import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.SenderType;
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
import art.arcane.edict.api.request.Node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.react.React;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Edict class represents the main processing and controlling entity of the Edict command handling system.
 * It encapsulates the primary functionality of the system and holds all necessary components
 * such as parserPackages, contextResolverPackages, endpoints, contextResolvers and parsers.
 * <br><br>
 * This class is equipped with initialization capabilities to load default parsers and context resolvers
 * and to record detailed status information.
 *
 * @see EdictParser
 * @see EdictEndpoint
 * @see EdictContextResolver
 */
@Data
@Builder
@AllArgsConstructor
public class Edict implements CommandExecutor, TabCompleter {
    /**
     * List of parser packages for the Edict system. The parsers in these packages are used to convert input strings into their corresponding objects.
     * By default, this list is initialized as an empty ArrayList.
     */
    @Singular("parserPackage")
    private final List<String> parserPackages;

    /**
     * List of context resolver packages for the Edict system. The context resolvers in these packages are used to derive contextual data from input commands.
     * By default, this list is initialized as an empty ArrayList.
     */
    @Singular("contextResolverPackage")
    private final List<String> contextResolverPackages;

    /**
     * List of endpoints that represent the commands handled by the Edict system.
     */
    @Builder.Default
    private List<EdictEndpoint> endpoints = new CopyOnWriteArrayList<>();

    /**
     * Set of context resolvers for the Edict system. These resolvers are used to derive contextual data from input commands.
     */
    @Singular
    private final Set<EdictContextResolver<?>> contextResolvers = defaultContextResolvers();

    /**
     * Set of parsers for the Edict system. These parsers are used to convert input strings into their corresponding objects.
     */
    @Singular
    private final Set<EdictParser<?>> parsers = defaultParsers();

    /**
     * Initializes the Edict system by loading the default parsers and context resolvers.
     * The detailed status of the system, including the number of endpoints, parsers, and context resolvers, is also reported.
     */
    public Edict initialize(org.bukkit.command.PluginCommand command) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        parsers.addAll(defaultParsers(parserPackages));
        contextResolvers.addAll(defaultContextResolvers(contextResolverPackages));
        React.verbose("Edict Endpoints: " + endpoints.size());
        React.verbose("Edict Parsers: " + parsers.size());
        React.verbose("Edict Context Resolvers: " + contextResolvers.size());
        React.verbose("Edict Initialized in " + Form.f(p.getMilliseconds(), 2) + "ms");
        command.setExecutor(this);
        command.setTabCompleter(this);
        return this;
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

    /**
     * Returns the root package name where the Edict class resides.
     *
     * @return the root package name as a String.
     */
    public static String getEdictRootPackage() {
        return Edict.class.getPackageName();
    }

    /**
     * Returns a specific sub-package name within the Edict root package.
     * <br><br>
     * If the sub-package name does not start with a dot (.), the method appends it at the beginning to form the correct structure.
     *
     * @param sub the name of the sub-package.
     * @return the full package name as a String.
     */
    public static String getEdictPackage(String sub) {
        if(!sub.startsWith(".")) {
            sub = "." + sub;
        }

        return getEdictRootPackage() + sub;
    }

    public Stream<EdictResponse> streamResponses(EdictRequest request){
        return streamResponses(request, false);
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
    public Stream<EdictResponse> streamResponses(EdictRequest request, boolean soft){
        List<EdictResponse> responses = endpoints.stream().map(i -> request.pathMatch(i, soft))
            .filter(Objects::nonNull)
            .map(i -> respond(i, i.getPair())).collect(Collectors.toList());

        if(responses.isEmpty()) {
            return Stream.empty();
        }

        List<String> header = request.getHeaderPath();
        List<Node> bestNodes = new ArrayList<>();

        if(soft && request.shouldTrim()) {
            header.add("");
        }

        React.verbose("HEADER: " + header);
        React.verbose("RAW: " + request.getRawArgs());
        React.verbose("ENDPOINT: " + responses.get(0).getEndpoint().getNodes());

        for(int ii = 0; ii < header.size() - 1; ii++) {
            int ix = ii;
            String i = header.get(ii);
            responses.stream().min(Comparator.comparingInt(a -> a.getEndpoint().getNodes().get(ix).getDistance(i)))
                .ifPresent(p -> bestNodes.add(p.getEndpoint().getNodes().get(ix)));

            responses.removeIf(j -> {
                if(!j.getEndpoint().getNodes().get(ix).matches(bestNodes.get(ix)))
                {
                    React.verbose("BUF: " + String.join(" ", header) + " DROP " + j.getEndpoint().getNodes().stream().map(ff -> ff.toString()).collect(Collectors.joining(" ")) + " because enode " + j.getEndpoint().getNodes().get(ix).toString() + " doesnt match " + bestNodes.get(ix).toString());
                    return true;
                }
                return false;
            });
        }

        return responses.stream();
    }

    /**
     * Executes a given command with a specific sender. This method creates an {@link EdictContext}
     * for the execution and invokes the executor for the appropriate endpoint.
     *
     * @param sender  the originator of the command.
     * @param command the command to be executed.
     * @return an {@link Optional} that contains an {@link EdictResponse} if the command execution was successful;
     *         otherwise, if there's no corresponding endpoint for the command, returns an empty Optional.
     */
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

    /**
     * Responds to an {@link EdictRequest}. This method finds the most suitable response to the request,
     * which is determined by the minimum score offset.
     *
     * @param request the {@link EdictRequest} to respond to.
     * @return an {@link Optional} that contains the most suitable {@link EdictResponse} if found;
     *         otherwise, if no suitable response is found, returns an empty Optional.
     */
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
        request.trimFor(endpoint);
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
                EdictFieldResponse fr = EdictFieldResponse.builder()
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
                    .confidence(confidence.get()).build();

                // todo: fix
                if(i.getDefaultValue() != null && fr.getValue() == null || fr.getConfidence().equals(Confidence.INVALID)) {
                    React.verbose("  Applying default value to " + i.getNames().get(0) + " -> " + i.getDefaultValue());
                    fr.setValue(i.getDefaultValue());
                }

                fields.add(fr);
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


    public static List<String> enhance(List<String> args, boolean trim) {
        return enhanceMappings(enhanceQuotes(args, trim));
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

    public Stream<EdictEndpoint> searchEndpoints(EdictRequest request) {
        return null;
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
        if(a == b) {
            return 0;
        }

        if(Character.toLowerCase(a) == Character.toLowerCase(b)) {
            return 1;
        }

        return 4;
    }

    /**
     * Registers one or more endpoints to this instance.
     *
     * @param endpoints The endpoints to be registered.
     */
    public Edict register(EdictEndpoint... endpoints) {
        for(EdictEndpoint i : endpoints) {
            this.endpoints.add(i);
            React.verbose("Registered Command " + i.getUsage());
        }

        return this;
    }

    /**
     * Registers one or more endpoints in all classes in a package parent.
     *
     * @param superPackage The package the class must at least start with to check
     */
    public Edict registerPackage(String superPackage) {
        Curse.whereInPackage(getClass(), (c) -> true, superPackage)
            .filter((c) -> c.declaredMethods().anyMatch(i -> i.isStatic() && !i.isNative() && i.isAnnotated(Edict.Command.class)))
            .peek(i -> React.verbose("?? Command " + i.toString()))
            .forEach(this::registerClass);

        return this;
    }

    /**
     * Register a cursed component (class) as an endpoint using annotations checks all static methods for @Command annotations
     * @param component The component to register
     */
    public Edict registerClass(CursedComponent component) {
        try
        {
            component.declaredMethods().filter(m -> m.isStatic() && !m.isNative() && m.isAnnotated(Edict.Command.class)).forEach(this::registerMethod);
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }
        return this;
    }

    /**
     * Register a cursed method as an endpoint using annotations
     * @param method The method to register
     */
    public Edict registerMethod(CursedMethod method) {
        if(!method.isStatic()) {
            throw new RuntimeException("Cannot register non-static method " + method.method().getName() + " as an endpoint.");
        }

        if(method.isNative()) {
            throw new RuntimeException("Cannot register native method " + method.method().getName() + " as an endpoint.");
        }

        if(!method.isAnnotated(Edict.Command.class)) {
            throw new RuntimeException("Cannot register method " + method.method().getName() + " as an endpoint because it is not annotated with @Edict.Command.");
        }

        EdictEndpoint.EdictEndpointBuilder builder = EdictEndpoint.builder();
        builder.command(method.annotated(Edict.Command.class).value());
        boolean allRequiredFields = false;

        if(method.isAnnotated(Edict.Aliases.class)) {
            builder.aliases(List.of(method.annotated(Edict.Aliases.class).value()));
        }

        if(method.isAnnotated(Edict.Permission.class)){
            builder.permissions(List.of(method.annotated(Edict.Permission.class).value()));
        }

        if(method.isAnnotated(Edict.PlayerOnly.class)) {
            builder.senderType(SenderType.PLAYER);
        } else if(method.isAnnotated(Edict.ConsoleOnly.class)) {
            builder.senderType(SenderType.CONSOLE);
        }

        if(method.isAnnotated(Edict.Description.class)) {
            builder.description(method.annotated(Edict.Description.class).value());
        }

        if(method.isAnnotated(Edict.Required.class)) {
            allRequiredFields = true;
        }

        for(Parameter i : method.method().getParameters()) {
            EdictField.EdictFieldBuilder fbuilder = EdictField.builder();
            fbuilder.name(i.getName());
            fbuilder.required(allRequiredFields);

            if(i.isAnnotationPresent(Edict.Required.class)) {
                fbuilder.required(true);
            } else if(i.isAnnotationPresent(Edict.NotRequired.class)) {
                fbuilder.required(false);
            }

            if(i.isAnnotationPresent(Edict.Default.class)) {
                fbuilder.defaultValue(i.getAnnotation(Edict.Default.class).value());
            }

            if(i.isAnnotationPresent(Edict.Description.class)) {
                fbuilder.description(i.getAnnotation(Edict.Description.class).value());
            }

            if(i.isAnnotationPresent(Edict.Aliases.class)) {
                for(String j : i.getAnnotation(Edict.Aliases.class).value()) {
                    fbuilder.name(j);
                }
            }

            if(i.isAnnotationPresent(Edict.Permission.class)) {
                fbuilder.permissions(List.of(i.getAnnotation(Edict.Permission.class).value()));
            }

            builder.field(fbuilder.build());
        }

        return register(builder
            .executor((i) -> {
                React.verbose("Executed " + i.getEndpoint().getUsage());

                Object[] o = new Object[method.method().getParameterCount()];
                int m = 0;
                for(EdictFieldResponse j : i.getResponse().getFields()) {
                    try {
                        o[m] = j.getValue();
                    }

                    catch(Throwable e) {
                        if(j.getField().isRequired()) {
                            i.getSender().sendMessage(ChatColor.RED + "Error: Parameter " + j.getField().getNames().get(0) + " is required!");
                            i.getSender().sendMessage(ChatColor.YELLOW + "Usage: " + i.getEndpoint().getUsage());
                            return;
                        }
                    }

                    React.verbose(" par" + (m++) +  j.getField().getNames().get(0) + " -> " + j.getValue());
                }

                method.invoke(o);
            })
            .build());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        execute(sender, command.getName() + " " + String.join(" ", args)).ifPresentOrElse(i-> {
            // When it went through
        }, () -> sender.sendMessage(ChatColor.RED + "Unknown react command. Type \"/help\" for help."));
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        List<String> s = new ArrayList<>();
        s.add(command.getName());
        s.addAll(Arrays.asList(args));
        React.verbose(s.toString());
        EdictRequest r = request(s);
        List<String> options = new ArrayList<>();
        List<EdictResponse> possible = streamResponses(r, true).sorted(Comparator.comparingInt(EdictResponse::getScoreOffset)).toList();

        for(EdictResponse i : possible) {
            List<String> path = i.getEndpoint().getPath();

            if(path.size() > args.length) {
                options.add(path.get(args.length));
            }
        }

        return options;
    }

    /**
     * Define the command header to match this method.
     * This is the command that will be used to invoke the method. Do not specify arguments.
     * If it's a root command simply type the command. You can use a slash, but it's not required.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        String value();
    }

    /**
     * Define a list of potential aliases for either the command or the parameters.
     * For commands re-type out the command, for example if the @Command was /plugin monitor, a valid alias would be
     * /plugin observe. Avoid adding aliases such as /plugin mon, because the matcher will already figure this out for you.
     * Only use aliases for wildly different words that would not be matched by the matcher very well.
     * <br><br>
     * For parameters, the base name is captured by the method parameter name, but you can add aliases to it in the same way
     */
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Aliases {
        String[] value();
    }

    /**
     * Require a permission for the command to be executed.
     * <br><br>
     * Parameter Edge Cases for when using this annotation on method parameters:
     * <ul>
     *     <li>If the parameter is contextual, it will be null if they lack the parameter even if there is a context</li>
     *     <li>If the parameter is required, this command will fail the permission check OR fail the required check. Do not do this.</li>
     *     <li>If the parameter is non-null but the confidence is INVALID, it will be set to null and be allowed</li>
     *     <li>If the parameter is non-null, LOW OR HIGH, permission error will happen and it wont run</li>
     * </ul>
     */
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Permission {
        String[] value();
    }

    /**
     * Indicate that this command can only be run by players. By default commands can be run by both players and the console.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PlayerOnly {

    }

    /**
     * Indicate that this command can only be run by the console. By default commands can be run by both players and the console.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ConsoleOnly {

    }

    /**
     * Denote that if this parameter is not specified in the command, a context grab will fill this parameter. For example:
     * @Contextual Player p, ...
     * This would grab the player that executed the command if it was a player.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Contextual {

    }

    /**
     * Denote that this parameter must be non-null and the confidence cannot be INVALID.
     * If you specify this on a method, it will enforce that all parameters are required.
     */
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Required {

    }

    /**
     * All parameters are optional by default. This is only used if you specify @Required on a method for all
     * parameters but have only one optional
     */
    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotRequired {

    }

    /**
     * Provide a default value to be parsed into this parameter if it's not specified in the command or if the
     * confidence is INVALID
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Default {
        String value();
    }

    /**
     * Define a description for the command. This will be displayed in the help menu.
     * You can also use this on parameters
     */
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Description {
        String value();
    }
}

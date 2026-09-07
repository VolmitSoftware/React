package art.arcane.react.web;

import art.arcane.react.api.web.WebRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebRoleTest {

    static Stream<Arguments> fromIdCases() {
        return Stream.of(
            Arguments.of("viewer", WebRole.VIEWER, "viewer"),
            Arguments.of("OPERATOR", WebRole.OPERATOR, "operator"),
            Arguments.of(" Admin ", WebRole.ADMIN, "admin"),
            Arguments.of(null, null, null),
            Arguments.of("", null, null),
            Arguments.of("superuser", null, null)
        );
    }

    static Stream<Arguments> roleScopes() {
        return Stream.of(
            Arguments.of(WebRole.VIEWER, Set.of("read")),
            Arguments.of(WebRole.OPERATOR, Set.of("read", "op:execute")),
            Arguments.of(WebRole.ADMIN, Set.of("read", "op:execute", "admin", "console:read", "console:execute"))
        );
    }

    static Stream<Arguments> scopesForCases() {
        return Stream.of(
            Arguments.of(null, WebRole.VIEWER.scopes()),
            Arguments.of("operator", WebRole.OPERATOR.scopes()),
            Arguments.of("bogus", Set.of())
        );
    }

    @ParameterizedTest(name = "fromId({0})")
    @MethodSource("fromIdCases")
    void fromIdTrimsAndLowercasesKnownIdsAndReturnsNullForBlankOrUnknownIds(String id, WebRole expectedRole, String expectedCanonicalId) {
        WebRole role = WebRole.fromId(id);
        assertEquals(expectedRole, role);
        assertEquals(expectedCanonicalId, role == null ? null : role.id());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roleScopes")
    void roleScopesGrantExactlyTheAuthorityForThatRole(WebRole role, Set<String> expectedScopes) {
        assertEquals(expectedScopes, role.scopes());
    }

    @ParameterizedTest(name = "scopesFor({0})")
    @MethodSource("scopesForCases")
    void scopesForFallsBackToViewerForNullAndYieldsNothingForUnknownIds(String id, Set<String> expectedScopes) {
        assertEquals(expectedScopes, WebRole.scopesFor(id));
    }

    @ParameterizedTest(name = "resolveRoleId({0})")
    @CsvSource(value = {
        "NIL, viewer",
        "viewer, viewer"
    }, nullValues = "NIL")
    void resolveRoleIdDefaultsToViewerAndEchoesKnownIds(String id, String expected) {
        assertEquals(expected, WebRole.resolveRoleId(id));
    }
}

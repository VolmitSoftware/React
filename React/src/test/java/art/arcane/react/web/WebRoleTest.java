package art.arcane.react.web;

import art.arcane.react.api.web.WebRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            Arguments.of(WebRole.VIEWER, List.of("read")),
            Arguments.of(WebRole.OPERATOR, List.of("read", "op:execute")),
            Arguments.of(WebRole.ADMIN, List.of("read", "op:execute", "admin", "console:read", "console:execute"))
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
    void roleScopesGrantExactlyTheAuthorityForThatRole(WebRole role, List<String> expectedScopes) {
        assertEquals(expectedScopes, role.scopes());
        assertEquals(role.id(), WebRole.roleOf(expectedScopes));
    }

    @Test
    void theDefaultPairingLeavesOutAdminAndTheConsole() {
        assertEquals(List.of("read", "op:execute"), WebRole.DEFAULT.scopes());
    }

    @Test
    void scopesParseInListOrderAndUnknownOnesAreNamed() {
        assertEquals(List.of("read", "console:read"), WebRole.parseScopes(" console:read , READ,read"));
        assertEquals("custom", WebRole.roleOf(List.of("read", "console:read")));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> WebRole.parseScopes("read,shell"));
        assertEquals("shell", unknown.getMessage());
    }
}

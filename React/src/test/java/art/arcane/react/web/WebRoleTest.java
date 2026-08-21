package art.arcane.react.web;

import art.arcane.react.api.web.WebRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebRoleTest {

    @Test
    void viewerScopesContainOnlyRead() {
        assertEquals(Set.of("read"), WebRole.VIEWER.scopes());
    }

    @Test
    void operatorScopesContainReadAndOpExecute() {
        assertEquals(Set.of("read", "op:execute"), WebRole.OPERATOR.scopes());
    }

    @Test
    void adminScopesContainReadOperationsAndConsoleAuthority() {
        assertEquals(
            Set.of("read", "op:execute", "admin", "console:read", "console:execute"),
            WebRole.ADMIN.scopes()
        );
    }

    @Test
    void fromIdCaseInsensitiveLowercase() {
        assertEquals(WebRole.VIEWER, WebRole.fromId("viewer"));
    }

    @Test
    void fromIdCaseInsensitiveUppercase() {
        assertEquals(WebRole.OPERATOR, WebRole.fromId("OPERATOR"));
    }

    @Test
    void fromIdCaseInsensitiveMixedWithWhitespace() {
        assertEquals(WebRole.ADMIN, WebRole.fromId(" Admin "));
    }

    @Test
    void fromIdNullReturnsNull() {
        assertNull(WebRole.fromId(null));
    }

    @Test
    void fromIdBlankReturnsNull() {
        assertNull(WebRole.fromId(""));
    }

    @Test
    void fromIdUnknownReturnsNull() {
        assertNull(WebRole.fromId("superuser"));
    }

    @Test
    void scopesForNullReturnsViewerScopes() {
        assertEquals(WebRole.VIEWER.scopes(), WebRole.scopesFor(null));
    }

    @Test
    void scopesForOperatorReturnsOperatorScopes() {
        assertEquals(WebRole.OPERATOR.scopes(), WebRole.scopesFor("operator"));
    }

    @Test
    void scopesForBogusReturnsEmptySet() {
        assertTrue(WebRole.scopesFor("bogus").isEmpty());
    }

    @Test
    void resolveRoleIdNullReturnsDefaultViewer() {
        assertEquals("viewer", WebRole.resolveRoleId(null));
    }

    @Test
    void resolveRoleIdViewerReturnsViewer() {
        assertEquals("viewer", WebRole.resolveRoleId("viewer"));
    }

    @Test
    void defaultRoleIdIsViewer() {
        assertEquals("viewer", WebRole.DEFAULT_ROLE_ID);
    }

    @Test
    void idMethodReturnsCanonicalLowercaseId() {
        assertEquals("viewer", WebRole.VIEWER.id());
        assertEquals("operator", WebRole.OPERATOR.id());
        assertEquals("admin", WebRole.ADMIN.id());
    }
}

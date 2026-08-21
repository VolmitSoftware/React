package art.arcane.react.core.controller;

import art.arcane.react.content.directorcommand.CommandReact;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DirectorCommandControllerLegacyParityTest {

    @Test
    public void normalizeLegacyArgsMapsEnvironmentRootsToInfo() {
        Assertions.assertArrayEquals(
                new String[]{"environment", "info"},
                DirectorCommandController.normalizeLegacyArgs(new String[]{"environment"})
        );
        Assertions.assertArrayEquals(
                new String[]{"environment", "info"},
                DirectorCommandController.normalizeLegacyArgs(new String[]{"env"})
        );
    }

    @Test
    public void normalizeLegacyArgsLeavesOtherCommandsUntouched() {
        String[] args = new String[]{"benchmark", "cpu"};
        Assertions.assertSame(args, DirectorCommandController.normalizeLegacyArgs(args));
    }

    @Test
    public void reactDirectorTreePreservesLegacyReactSpellings() {
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandReact());
        DirectorRuntimeNode root = engine.getRoot();

        assertExactPath(root, "version");
        assertExactPath(root, "v");
        assertExactPath(root, "reload");
        assertExactPath(root, "rl");
        assertExactPath(root, "benchmark", "cpu");
        assertExactPath(root, "benchmark", "processor");
        assertExactPath(root, "bench", "processor");
        assertExactPath(root, "bench", "cpu");
        assertExactPath(root, "benchmark", "memory");
        assertExactPath(root, "benchmark", "ram");
        assertExactPath(root, "bench", "ram");
        assertExactPath(root, "bench", "memory");
        assertExactPath(root, "environment", "info");
        assertExactPath(root, "env", "info");
    }

    private static void assertExactPath(DirectorRuntimeNode root, String... path) {
        DirectorRuntimeNode cursor = root;
        for (String token : path) {
            cursor = findExactChild(cursor, token);
            Assertions.assertNotNull(cursor, "Missing Director token: " + token);
        }
    }

    private static DirectorRuntimeNode findExactChild(DirectorRuntimeNode node, String token) {
        for (DirectorRuntimeNode child : node.getChildren()) {
            for (String name : child.allNames()) {
                if (name.equalsIgnoreCase(token)) {
                    return child;
                }
            }
        }

        return null;
    }
}

package art.arcane.react.mod;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

class ModFrameNamesTest {
    private static ModFrameNames names(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return ModFrameNames.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void srgMethodsMapInsideLambdas() throws IOException {
        ModFrameNames srg = names("react-frame-names 1\nclasses  0\nmethods m_ _ 2\n12 tick\n280296 generate\n");
        Assertions.assertEquals("generate", srg.readableMethod("m_280308_"));
        Assertions.assertEquals("tick", srg.readableMethod("m_12_"));
        Assertions.assertEquals("lambda$generate$3", srg.readableMethod("lambda$m_280308_$3"));
        Assertions.assertEquals("m_99_", srg.readableMethod("m_99_"));
        Assertions.assertEquals("run", srg.readableMethod("run"));
        Assertions.assertEquals("net.minecraft.world.level.chunk.ChunkStatus", srg.readableClass("net.minecraft.world.level.chunk.ChunkStatus"));
    }

    @Test
    void intermediaryClassesAndMethodsMap() throws IOException {
        ModFrameNames tiny = names("react-frame-names 1\nclasses net.minecraft. 2\nclass_2806 net.minecraft.world.level.chunk.ChunkStatus\n"
            + "class_4990$class_4991 net.minecraft.A$B\nmethods method_  1\n12154 generate\n");
        Assertions.assertEquals("net.minecraft.world.level.chunk.ChunkStatus", tiny.readableClass("net.minecraft.class_2806"));
        Assertions.assertEquals("net.minecraft.A$B", tiny.readableClass("net.minecraft.class_4990$class_4991"));
        Assertions.assertEquals("net.minecraft.class_1", tiny.readableClass("net.minecraft.class_1"));
        Assertions.assertEquals("generate", tiny.readableMethod("method_12154"));
        Assertions.assertEquals("method_121540", tiny.readableMethod("method_121540"));
    }

    @Test
    void badHeaderReadsAsEmpty() throws IOException {
        Assertions.assertTrue(names("something else\n").empty());
    }
}

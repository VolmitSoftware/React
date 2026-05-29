package art.arcane.react.core.bridge;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MappingsLoaderTest {

    @Test
    public void missingVersionFile_returnsEmptyMap() {
        MappingsLoader loader = new MappingsLoader("99.999");
        Assert.assertNull(loader.lookup("anything"));
    }

    @Test
    public void existingVersionFile_loadsEntry() {
        MappingsLoader loader = new MappingsLoader("1.21");
        MappingsLoader.MappingEntry entry = loader.lookup("HopperBlockEntity.cooldownTime");
        Assert.assertNotNull("Expected HopperBlockEntity.cooldownTime entry from 1.21.json", entry);
        Assert.assertEquals("cooldownTime", entry.memberName());
        Assert.assertFalse(entry.classNames().isEmpty());
        Assert.assertTrue(entry.classNames().contains("net.minecraft.world.level.block.entity.HopperBlockEntity"));
    }

    @Test
    public void existingVersionFile_multipleEntries() {
        MappingsLoader loader = new MappingsLoader("1.21");
        MappingsLoader.MappingEntry addItem = loader.lookup("HopperBlockEntity.addItem");
        Assert.assertNotNull("Expected HopperBlockEntity.addItem entry from 1.21.json", addItem);
        Assert.assertEquals("addItem", addItem.memberName());
    }

    @Test
    public void malformedJson_returnsEmptyMap() {
        String badJson = "{ not valid json [[[";
        MappingsLoader loader = new MappingsLoader(
                new ByteArrayInputStream(badJson.getBytes(StandardCharsets.UTF_8)));
        Assert.assertNull(loader.lookup("anything"));
    }

    @Test
    public void emptyStream_returnsEmptyMap() {
        MappingsLoader loader = new MappingsLoader(
                new ByteArrayInputStream(new byte[0]));
        Assert.assertNull(loader.lookup("anything"));
    }

    @Test
    public void validStream_loadsEntries() {
        String json = "{ \"mcVersion\": \"test\", \"members\": { " +
                "\"Foo.bar\": { \"classNames\": [\"com.example.Foo\"], \"memberName\": \"bar\", \"kind\": \"FIELD\" } } }";
        MappingsLoader loader = new MappingsLoader(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        MappingsLoader.MappingEntry entry = loader.lookup("Foo.bar");
        Assert.assertNotNull(entry);
        Assert.assertEquals("bar", entry.memberName());
        Assert.assertEquals("com.example.Foo", entry.classNames().get(0));
    }
}

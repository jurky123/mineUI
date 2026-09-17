package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.ScrollViewNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDefinitionLoaderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        UiDefinitionLoader.clearCache();
    }

    private void writeDefinition(String app, String view, String json) throws IOException {
        Path dir = tempDir.resolve(app);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(view + ".json"), json);
    }

    @Test
    void loadsDevOverrideAndCreatesFreshTreeEachTime() throws Exception {
        writeDefinition("mineui", "test",
                "{ \"type\": \"scroll\", \"height\": 50, \"children\": [ {\"type\":\"box\",\"height\":10} ] }");

        UiDefinition first = UiDefinitionLoader.load("mineui", "test", tempDir);
        UiDefinition second = UiDefinitionLoader.load("mineui", "test", tempDir);

        assertInstanceOf(ScrollViewNode.class, first.root());
        assertNotSame(first.root(), second.root(), "每次打开必须构造新的节点树");
        assertTrue(first.source().startsWith("dev:"));
    }

    @Test
    void rejectsPathTraversalInAppAndView() {
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("..", "test", tempDir));
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("mineui", "../../evil", tempDir));
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("MineUI", "test", tempDir));
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("mineui", "te st", tempDir));
    }

    @Test
    void writeDevTemplateDoesNotOverwriteExistingFile() throws Exception {
        writeDefinition("mineui", "test", "{ \"type\": \"box\" }");
        assertFalse(UiDefinitionLoader.writeDevTemplate("mineui", "test", tempDir));
    }

    @Test
    void writeDevTemplateRejectsUnsafeNames() {
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.writeDevTemplate("../evil", "test", tempDir));
    }

    @Test
    void missingDefinitionThrows() {
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("mineui", "nope", tempDir));
    }

    @Test
    void malformedDevJsonThrows() throws Exception {
        writeDefinition("mineui", "test", "{ not json");
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.load("mineui", "test", tempDir));
    }

    @Test
    void loadsServerProvidedDefinition() throws Exception {
        JsonObject json = JsonParser.parseString("""
                { "type": "scroll", "height": 40, "children": [ {"type":"box","height":6} ] }
                """).getAsJsonObject();

        UiDefinition definition = UiDefinitionLoader.loadProvided("skin", "browser", json);
        assertInstanceOf(ScrollViewNode.class, definition.root());
        assertEquals("server", definition.source());
    }

    @Test
    void providedDefinitionGetsFreshTreeEachOpen() throws Exception {
        JsonObject json = JsonParser.parseString("{ \"type\": \"box\", \"height\": 10 }").getAsJsonObject();
        UiDefinition first = UiDefinitionLoader.loadProvided("skin", "browser", json);
        UiDefinition second = UiDefinitionLoader.loadProvided("skin", "browser", json);
        assertNotSame(first.root(), second.root());
    }

    @Test
    void loadProvidedRejectsUnsafeNames() {
        JsonObject json = JsonParser.parseString("{ \"type\": \"box\" }").getAsJsonObject();
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.loadProvided("..", "browser", json));
        assertThrows(UiSpecException.class, () -> UiDefinitionLoader.loadProvided("skin", "B rowser", json));
    }

    @Test
    void writeDevJsonDoesNotOverwrite() throws Exception {
        JsonObject json = JsonParser.parseString("{ \"type\": \"box\" }").getAsJsonObject();
        assertTrue(UiDefinitionLoader.writeDevJson("skin", "browser", json, tempDir));
        assertFalse(UiDefinitionLoader.writeDevJson("skin", "browser", json, tempDir));
    }
}

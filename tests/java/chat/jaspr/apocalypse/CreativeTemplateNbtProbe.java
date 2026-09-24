package chat.jaspr.apocalypse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import net.minecraft.server.v1_12_R1.MojangsonParser;
import net.minecraft.server.v1_12_R1.NBTTagCompound;

/** Validates generated creative templates with Minecraft 1.12.2's real SNBT parser. */
public final class CreativeTemplateNbtProbe {
    private CreativeTemplateNbtProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one UTF-8 catalogue fixture path");
        }

        Path fixture = Paths.get(args[0]);
        List<String> rows = Files.readAllLines(fixture, StandardCharsets.UTF_8);
        int checked = 0;
        for (String row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            String[] columns = row.split("\\t", 3);
            if (columns.length != 3) {
                throw new AssertionError("Malformed fixture row: " + row);
            }

            String expectedId = columns[0];
            short expectedModel = Short.parseShort(columns[1]);
            NBTTagCompound root = MojangsonParser.parse(columns[2]);
            assertEquals("minecraft:" + materialFor(root), root.getString("id"), expectedId + " material");
            assertEquals((byte) 1, root.getByte("Count"), expectedId + " count");
            assertEquals(expectedModel, root.getShort("Damage"), expectedId + " model");

            NBTTagCompound itemTag = root.getCompound("tag");
            NBTTagCompound marker = itemTag.getCompound("JasprCreative");
            assertEquals(expectedId, marker.getString("id"), expectedId + " marker");
            checked++;
        }

        if (checked != 89) {
            throw new AssertionError("Expected 89 templates, checked " + checked);
        }
        System.out.println("CREATIVE_TEMPLATE_NBT_PASS count=" + checked);
    }

    private static String materialFor(NBTTagCompound root) {
        String material = root.getString("id");
        return material.startsWith("minecraft:") ? material.substring("minecraft:".length()) : material;
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}

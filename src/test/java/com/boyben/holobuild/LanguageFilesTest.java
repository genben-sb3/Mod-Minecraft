package com.boyben.holobuild;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks that every piece of text the mod asks for actually exists.
 *
 * A missing translation key does not crash and does not fail the build. It
 * shows up in the game as raw text like "tooltip.holobuild.selection.keeps"
 * sitting in the middle of the screen, which is the sort of thing that gets
 * noticed by a player rather than by a programmer. Both tests here are cheap
 * and catch it before it ships.
 */
class LanguageFilesTest {

    private static final String LANG = "/assets/holobuild/lang/";

    /**
     * Any string in the source that looks like one of our translation keys.
     *
     * Matching the shape of the key rather than the call around it is
     * deliberate: keys get handed around in variables and picked with ?:, so
     * looking for "translatable(" would quietly miss some of them - including
     * the corner messages on the Selection Wand.
     */
    private static final Pattern KEY_IN_SOURCE = Pattern.compile(
            "\"((?:message|screen|tooltip|guide|key|item|itemGroup)"
                    + "\\.holobuild(?:\\.[A-Za-z0-9_.]+)?)\"");

    private static JsonObject load(String file) throws IOException {
        try (InputStream in = LanguageFilesTest.class.getResourceAsStream(LANG + file)) {
            if (in == null) {
                throw new IOException("Sprachdatei nicht im Klassenpfad: " + LANG + file);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    @DisplayName("English and German cover exactly the same keys")
    void bothLanguagesMatch() throws IOException {
        JsonObject english = load("en_us.json");
        JsonObject german = load("de_de.json");

        Set<String> missingGerman = new TreeSet<>(english.keySet());
        missingGerman.removeAll(german.keySet());
        Set<String> missingEnglish = new TreeSet<>(german.keySet());
        missingEnglish.removeAll(english.keySet());

        if (!missingGerman.isEmpty() || !missingEnglish.isEmpty()) {
            fail("Sprachdateien laufen auseinander."
                    + "\n  Fehlt auf Deutsch: " + missingGerman
                    + "\n  Fehlt auf Englisch: " + missingEnglish);
        }
    }

    @Test
    @DisplayName("No text is left empty in either language")
    void noEmptyText() throws IOException {
        for (String file : List.of("en_us.json", "de_de.json")) {
            JsonObject texts = load(file);
            for (String key : texts.keySet()) {
                JsonElement value = texts.get(key);
                assertTrue(value.isJsonPrimitive() && !value.getAsString().isBlank(),
                        file + ": '" + key + "' hat keinen Text");
            }
        }
    }

    @Test
    @DisplayName("No guide line is long enough to swallow a whole page")
    void guideLinesStayShort() throws IOException {
        // The screen wraps, so a long line is not cut off - it just takes two
        // rows instead of one, and a page of those runs off the bottom. This is
        // a ceiling against a whole paragraph landing in a single key, not an
        // attempt to measure pixels: that depends on the font and the GUI scale.
        int limit = 70;
        for (String file : List.of("en_us.json", "de_de.json")) {
            JsonObject texts = load(file);
            for (String key : texts.keySet()) {
                if (!key.startsWith("guide.holobuild.")) {
                    continue;
                }
                String text = texts.get(key).getAsString();
                assertTrue(text.length() <= limit,
                        file + ": '" + key + "' hat " + text.length()
                                + " Zeichen, erlaubt sind " + limit);
            }
        }
    }

    @Test
    @DisplayName("Every key the code asks for exists in the language file")
    void everyKeyUsedInCodeExists() throws IOException {
        // Handed in by build.gradle: with Minecraft on the classpath the tests
        // run from build/minecraft-junit, where "src/main/java" means nothing.
        String configured = System.getProperty("holobuild.sources");
        assertTrue(configured != null && Files.isDirectory(Path.of(configured)),
                "Quellordner nicht gefunden (holobuild.sources = " + configured
                        + ") - der Test kann so nichts pruefen");
        Path sources = Path.of(configured);

        JsonObject english = load("en_us.json");
        Set<String> missing = new TreeSet<>();

        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = KEY_IN_SOURCE.matcher(code);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // Item and creative-tab names are built by Minecraft from
                    // the registry name, so the code never spells them out;
                    // anything else has to be in the file.
                    if (!english.has(key)) {
                        missing.add(key + "  (" + sources.relativize(file) + ")");
                    }
                }
            }
        }

        if (!missing.isEmpty()) {
            fail("Diese Schluessel benutzt der Code, aber en_us.json kennt sie nicht:\n  "
                    + String.join("\n  ", missing));
        }
    }
}

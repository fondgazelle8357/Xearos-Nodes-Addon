package me.fond.nodesoverlay.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerConfigManagerTest {

    private static final List<String> KNOWN_CACHE_FILES = List.of(
            "towns.json",
            "world.json",
            "ports.json",
            "http-metadata.json",
            "towns.json.tmp",
            "world.json.tmp",
            "ports.json.tmp",
            "http-metadata.json.tmp"
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesValidLoadedProfileWhenNormalizedSaveBackFails() throws Exception {
        String serverAddress = "dev.example.test:25565";
        ServerConfigManager writer = new ServerConfigManager(temporaryDirectory);
        ServerSettings original = writer.activate(serverAddress);
        original.enabled = true;
        original.townsUrl = "https://example.test/custom-towns.json";
        original.worldUrl = "https://example.test/custom-world.json";
        original.portsUrl = "https://example.test/custom-ports.json";
        original.warIconScale = 1.65D;
        original.blacklistedUuids.add("00000000-0000-0000-0000-000000000003");
        writer.save();

        Path serverDirectory = writer.serverDirectory();
        Files.createDirectory(serverDirectory.resolve("settings.json.tmp"));

        ServerConfigManager reader = new ServerConfigManager(temporaryDirectory);
        ServerSettings loaded = reader.activate(serverAddress);

        assertTrue(loaded.enabled);
        assertEquals(original.townsUrl, loaded.townsUrl);
        assertEquals(original.worldUrl, loaded.worldUrl);
        assertEquals(original.portsUrl, loaded.portsUrl);
        assertEquals(1.65D, loaded.warIconScale);
        assertTrue(loaded.blacklistedUuids.contains(
                "00000000-0000-0000-0000-000000000003"
        ));
    }

    @Test
    void clearsOnlyKnownCacheFilesForTheActivatedProfile() throws Exception {
        ServerConfigManager selected = new ServerConfigManager(temporaryDirectory);
        selected.activate("selected.example.test:25565");
        Path selectedCache = selected.cacheDirectory();
        createCacheFixture(selectedCache);
        Path unrelatedFile = selectedCache.resolve("keep-me.txt");
        Files.writeString(unrelatedFile, "unrelated");

        ServerConfigManager other = new ServerConfigManager(temporaryDirectory);
        other.activate("other.example.test:25565");
        Path otherCache = other.cacheDirectory();
        createCacheFixture(otherCache);

        selected.clearCacheFiles();

        for (String fileName : KNOWN_CACHE_FILES) {
            assertFalse(Files.exists(selectedCache.resolve(fileName)), fileName);
            assertTrue(Files.exists(otherCache.resolve(fileName)), "other profile " + fileName);
        }
        assertTrue(Files.exists(unrelatedFile));
    }

    @Test
    void migratesLegacyInvisibleBordersToDarkOutlineDefaults() {
        ServerSettings settings = new ServerSettings();
        settings.configurationVersion = 0;
        settings.borderOpacity = 0;
        settings.internalBorderOpacity = 0;

        settings.normalize();

        assertEquals(6, settings.configurationVersion);
        assertEquals(235, settings.borderOpacity);
        assertEquals(155, settings.internalBorderOpacity);
        assertTrue(settings.blacklistedUuids.isEmpty());
        assertFalse(settings.showOverviewTownNames);
        assertTrue(settings.showPortOwnerInLabels);
    }

    @Test
    void preservesExistingPortOwnerLabelsDuringVersionFiveMigration() {
        ServerSettings settings = new ServerSettings();
        settings.configurationVersion = 4;
        settings.showPortOwnerInLabels = false;

        settings.normalize();

        assertEquals(6, settings.configurationVersion);
        assertTrue(settings.showPortOwnerInLabels);
    }

    @Test
    void keepsCurrentWarIconSizeAsDefaultAndClampsInvalidValues() {
        ServerSettings settings = new ServerSettings();
        assertEquals(ServerSettings.DEFAULT_WAR_ICON_SCALE, settings.warIconScale);

        settings.warIconScale = 0.1D;
        settings.normalize();
        assertEquals(ServerSettings.MINIMUM_WAR_ICON_SCALE, settings.warIconScale);

        settings.warIconScale = 4.0D;
        settings.normalize();
        assertEquals(ServerSettings.MAXIMUM_WAR_ICON_SCALE, settings.warIconScale);

        settings.warIconScale = Double.NaN;
        settings.normalize();
        assertEquals(ServerSettings.DEFAULT_WAR_ICON_SCALE, settings.warIconScale);
    }

    private static void createCacheFixture(Path cacheDirectory) throws Exception {
        Files.createDirectories(cacheDirectory);
        for (String fileName : KNOWN_CACHE_FILES) {
            Files.writeString(cacheDirectory.resolve(fileName), fileName);
        }
    }
}

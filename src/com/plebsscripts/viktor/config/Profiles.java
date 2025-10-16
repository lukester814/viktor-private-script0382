package com.plebsscripts.viktor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plebsscripts.viktor.util.Logs;

import java.io.*;
import java.util.*;

/**
 * Manages multiple saved profiles (settings configurations).
 * Each profile is a JSON file: data/profiles/Bot1.json, Bot2.json, etc.
 *
 * Users can quick-switch between accounts or strategies via GUI.
 */
public class Profiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Load all profiles from data/profiles/ directory
     * @return Map of profileName -> Settings
     */
    public static Map<String, Settings> loadAll(File dataDir) {
        Map<String, Settings> profiles = new HashMap<>();

        File profilesDir = new File(dataDir, "profiles");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
            Logs.info("Created profiles directory: " + profilesDir.getPath());
            return profiles;
        }

        File[] files = profilesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            Logs.info("No profiles found in " + profilesDir.getPath());
            return profiles;
        }

        for (File file : files) {
            try {
                String profileName = file.getName().replace(".json", "");
                Settings settings = load(file);

                if (settings != null) {
                    profiles.put(profileName, settings);
                    Logs.info("Loaded profile: " + profileName);
                }
            } catch (Exception e) {
                Logs.warn("Failed to load profile " + file.getName() + ": " + e.getMessage());
            }
        }

        return profiles;
    }

    /**
     * Load a specific profile by name
     */
    public static Settings load(File dataDir, String profileName) {
        File profilesDir = new File(dataDir, "profiles");
        File file = new File(profilesDir, profileName + ".json");

        if (!file.exists()) {
            Logs.warn("Profile not found: " + profileName);
            return null;
        }

        return load(file);
    }

    /**
     * Load settings from a JSON file
     */
    private static Settings load(File file) {
        try (Reader reader = new FileReader(file)) {
            return GSON.fromJson(reader, Settings.class);
        } catch (Exception e) {
            Logs.warn("Failed to read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save a profile with a given name
     */
    public static boolean save(File dataDir, String profileName, Settings settings) {
        File profilesDir = new File(dataDir, "profiles");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }

        File file = new File(profilesDir, profileName + ".json");

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(settings, writer);
            Logs.info("Saved profile: " + profileName);
            return true;
        } catch (Exception e) {
            Logs.warn("Failed to save profile " + profileName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a profile
     */
    public static boolean delete(File dataDir, String profileName) {
        File profilesDir = new File(dataDir, "profiles");
        File file = new File(profilesDir, profileName + ".json");

        if (file.exists() && file.delete()) {
            Logs.info("Deleted profile: " + profileName);
            return true;
        }

        return false;
    }

    /**
     * Get list of all profile names
     */
    public static List<String> listNames(File dataDir) {
        List<String> names = new ArrayList<>();
        File profilesDir = new File(dataDir, "profiles");

        if (!profilesDir.exists()) {
            return names;
        }

        File[] files = profilesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName().replace(".json", ""));
            }
        }

        Collections.sort(names);
        return names;
    }

    /**
     * Create a default profile if none exist
     */
    public static void createDefaultIfNeeded(File dataDir) {
        List<String> existing = listNames(dataDir);

        if (existing.isEmpty()) {
            Settings defaultSettings = new Settings();
            defaultSettings.inputPath = "data/ge_flips.csv";
            defaultSettings.discord.enabled = false;

            save(dataDir, "Default", defaultSettings);
            Logs.info("Created default profile");
        }
    }

    /**
     * Duplicate an existing profile with a new name
     */
    public static boolean duplicate(File dataDir, String sourceName, String newName) {
        Settings source = load(dataDir, sourceName);
        if (source == null) {
            return false;
        }

        return save(dataDir, newName, source);
    }
}

package com.bizco.client.ui;

import java.util.prefs.Preferences;
import javafx.scene.Parent;

/**
 * Tracks the selected UI theme (blue/light or dark), applies it to the shell's root node via
 * a style class so descendant CSS token lookups resolve to the right palette, and remembers
 * the user's choice across restarts using the JDK's per-user preference store.
 */
public final class ThemeManager {

    public enum Theme {
        LIGHT,
        DARK
    }

    private static final String PREF_KEY = "theme";
    private static final String DARK_STYLE_CLASS = "theme-dark";

    private final Preferences preferences = Preferences.userNodeForPackage(ThemeManager.class);
    private Theme current;

    /** Reads the persisted theme (defaults to LIGHT/blue) and remembers it as current. */
    public Theme loadSavedTheme() {
        current = parse(preferences.get(PREF_KEY, Theme.LIGHT.name()));
        return current;
    }

    /** The active theme; loads the persisted value first if nothing has been applied yet. */
    public Theme current() {
        return current == null ? loadSavedTheme() : current;
    }

    /** Applies the given theme to the shell root and persists the choice. */
    public void apply(final Parent shellRoot, final Theme theme) {
        current = theme;
        shellRoot.getStyleClass().remove(DARK_STYLE_CLASS);
        if (theme == Theme.DARK) {
            shellRoot.getStyleClass().add(DARK_STYLE_CLASS);
        }
        preferences.put(PREF_KEY, theme.name());
    }

    /** Switches between LIGHT and DARK and applies/persists the new choice. */
    public void toggle(final Parent shellRoot) {
        apply(shellRoot, current() == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    private Theme parse(final String value) {
        try {
            return Theme.valueOf(value);
        } catch (final IllegalArgumentException | NullPointerException ex) {
            return Theme.LIGHT;
        }
    }
}

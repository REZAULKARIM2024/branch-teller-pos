package com.branchteller.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central translation lookup for the whole app. Backed by standard Java ResourceBundle
 * properties files under src/main/resources/i18n/messages*.properties. Swing doesn't
 * hot-swap already-built component text, so the convention here is: changing the locale
 * (setLocale) is followed by the caller rebuilding whatever window is currently showing
 * (see LoginFrame/MainFrame's language switcher).
 */
public final class Messages {

    private Messages() {}

    public static final Locale ENGLISH = new Locale("en");
    public static final Locale BANGLA  = new Locale("bn");
    public static final Locale SPANISH = new Locale("es");
    public static final Locale FRENCH  = new Locale("fr");
    public static final Locale ARABIC  = new Locale("ar");

    private static volatile Locale currentLocale = ENGLISH;
    private static volatile ResourceBundle bundle = load(ENGLISH);

    private static ResourceBundle load(Locale locale) {
        return ResourceBundle.getBundle("i18n.messages", locale);
    }

    /** Switches the active language. Does not touch any already-built UI -- callers rebuild. */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = load(locale);
    }

    public static Locale getLocale() {
        return currentLocale;
    }

    /** True for right-to-left languages (Arabic), used to set component orientation. */
    public static boolean isRtl() {
        return "ar".equals(currentLocale.getLanguage());
    }

    /** Plain lookup; returns the key itself if no translation is found (never throws). */
    public static String tr(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** Lookup with {0}/{1}/... placeholder substitution. */
    public static String tr(String key, Object... args) {
        return MessageFormat.format(tr(key), args);
    }

    /** Human-readable display names for the language picker, always shown in their own language. */
    public static String[] displayNames() {
        return new String[]{"English", "বাংলা", "Español", "Français", "العربية"};
    }

    public static Locale[] supportedLocales() {
        return new Locale[]{ENGLISH, BANGLA, SPANISH, FRENCH, ARABIC};
    }

    /** Maps a display name (as shown in the picker) back to its Locale. */
    public static Locale localeForDisplayName(String displayName) {
        String[] names = displayNames();
        Locale[] locales = supportedLocales();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(displayName)) return locales[i];
        }
        return ENGLISH;
    }

    public static String displayNameForLocale(Locale locale) {
        Locale[] locales = supportedLocales();
        String[] names = displayNames();
        for (int i = 0; i < locales.length; i++) {
            if (locales[i].getLanguage().equals(locale.getLanguage())) return names[i];
        }
        return names[0];
    }
}

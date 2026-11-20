package net.coolsimulations.floatingitems;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side translation table.
 *
 * <p>
 * The messages are loaded from {@code assets/floatingitems/lang/*.lang}. This keeps the translations out of the
 * Java control flow, makes the keys ordinary map lookups, and lets a translator add a language without editing
 * code.
 * </p>
 */
public final class FILang {

    private static final String DEFAULT_LOCALE = "en_us";
    private static final String LANG_PATH = "/assets/floatingitems/lang/";

    private static final Map<String, String> ALIASES;
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<String, Map<String, String>>();

    static {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put("de_at", "de_de");
        aliases.put("de_ch", "de_de");
        aliases.put("en_au", "en_us");
        aliases.put("en_ca", "en_us");
        aliases.put("en_gb", "en_us");
        aliases.put("en_nz", "en_us");
        aliases.put("en_7s", "en_pt");
        aliases.put("eo_uy", "eo");
        aliases.put("es_ar", "es_es");
        aliases.put("es_cl", "es_es");
        aliases.put("es_mx", "es_es");
        aliases.put("es_uy", "es_es");
        aliases.put("es_ve", "es_es");
        aliases.put("pt_br", "pt_pt");
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private FILang() {}

    /**
     * Looks up a translation in the configured server language, falling back to US English.
     *
     * @param text the translation key
     * @return the translated value, or {@code text} when the key is unknown
     */
    public static String getTranslations(String text) {

        String language = FIConfig.serverLang == null ? DEFAULT_LOCALE : FIConfig.serverLang.trim();
        language = language.toLowerCase(Locale.ROOT);
        language = ALIASES.containsKey(language) ? ALIASES.get(language) : language;

        String translated = table(language).get(text);
        if (translated == null && !DEFAULT_LOCALE.equals(language)) {
            translated = table(DEFAULT_LOCALE).get(text);
        }
        return translated == null ? text : translated;
    }

    private static Map<String, String> table(String language) {

        Map<String, String> cached = CACHE.get(language);
        if (cached != null) {
            return cached;
        }

        Map<String, String> loaded = load(language);
        Map<String, String> previous = CACHE.putIfAbsent(language, loaded);
        return previous == null ? loaded : previous;
    }

    private static Map<String, String> load(String language) {

        try (InputStream stream = FILang.class.getResourceAsStream(LANG_PATH + language + ".lang")) {
            if (stream == null) {
                return Collections.emptyMap();
            }

            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));

            Map<String, String> translations = new HashMap<String, String>();
            for (String key : properties.stringPropertyNames()) {
                translations.put(key, properties.getProperty(key));
            }
            return Collections.unmodifiableMap(translations);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}

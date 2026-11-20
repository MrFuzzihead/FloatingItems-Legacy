package net.coolsimulations.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the hand-written server-side translation table.
 *
 * <p>
 * {@code FILang} is pure Java: it never touches a world, an item or a client. That makes it the largest genuinely
 * headless-testable class in the mod, and these tests drive every language branch and every message key so the
 * translation table cannot silently regress.
 * </p>
 */
public class FILangTest {

    private static final String[] LANGUAGES = { "af_za", "ar_sa", "be_by", "bg_bg", "bs_ba", "ca_es", "cs_cz", "cy_gb",
        "da_dk", "de_at", "de_alg", "de_ch", "de_de", "el_gr", "en_us", "en_au", "en_ca", "en_gb", "en_nz", "en_7s",
        "en_pt", "en_ud", "en_ws", "eo", "eo_uy", "es_ar", "es_cl", "es_es", "es_mx", "es_uy", "es_ve", "fi_fi",
        "fr_fr", "hr_hr", "hu_hu", "it_it", "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "pt_pt", "ru_ru", "sv_se",
        "th_th", "tr_tr", "zh_cn", "zh_tw" };

    private static final String[] KEYS = { "floatingitems.update.display1", "floatingitems.update.display2",
        "floatingitems.update.display3", "fi.configgui.title" };

    private String previousLanguage;
    private Locale previousLocale;

    @Before
    public void rememberLanguage() {
        previousLanguage = FIConfig.serverLang;
        previousLocale = Locale.getDefault();
    }

    @After
    public void restoreLanguage() {
        FIConfig.serverLang = previousLanguage;
        Locale.setDefault(previousLocale);
    }

    @Test
    public void everyLanguageTranslatesEveryUpdateAndConfigKey() {
        for (String language : LANGUAGES) {
            FIConfig.serverLang = language;
            for (String key : KEYS) {
                String translated = FILang.getTranslations(key);
                assertNotNull("null translation for " + language + " / " + key, translated);
                assertFalse("untranslated key for " + language + " / " + key, key.equals(translated));
                assertFalse("empty translation for " + language + " / " + key, translated.isEmpty());
            }

            String title = FILang.getTranslations("fi.configgui.title");
            assertTrue(
                "config title for " + language + " should contain the mod name",
                title.contains(FIReference.MOD_NAME));
        }
    }

    @Test
    public void placeholdersArePreservedInTheLanguagesThatUseAsciiPercent() {
        String[] languages = { "en_us", "en_gb", "de_de", "fr_fr", "it_it", "es_es", "pt_br", "ru_ru", "ja_jp", "ko_kr",
            "pl_pl", "sv_se", "tr_tr", "af_za", "ar_sa", "bg_bg", "cs_cz", "da_dk", "fi_fi", "nl_nl" };
        for (String language : languages) {
            FIConfig.serverLang = language;
            assertEquals(
                "display1 should have two placeholders in " + language,
                2,
                countPlaceholders(FILang.getTranslations("floatingitems.update.display1")));
            assertEquals(
                "display3 should have two placeholders in " + language,
                2,
                countPlaceholders(FILang.getTranslations("floatingitems.update.display3")));
            assertEquals(
                "display2 should have no placeholders in " + language,
                0,
                countPlaceholders(FILang.getTranslations("floatingitems.update.display2")));
        }
    }

    @Test
    public void unknownLanguageFallsBackToUsEnglish() {
        String english = translation("en_us", "floatingitems.update.display1");
        FIConfig.serverLang = "xx_xx";
        assertEquals(english, FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void nullLanguageFallsBackToUsEnglish() {
        String english = translation("en_us", "floatingitems.update.display1");
        FIConfig.serverLang = null;
        assertEquals(english, FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void languageCodeIsCaseInsensitive() {
        String german = translation("de_de", "floatingitems.update.display1");
        FIConfig.serverLang = "DE_DE";
        assertEquals(german, FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void unknownKeyIsReturnedUnchanged() {
        FIConfig.serverLang = "en_us";
        assertEquals("floatingitems.does.not.exist", FILang.getTranslations("floatingitems.does.not.exist"));
    }

    @Test
    public void removedServerStopKeysFallBackToTheKey() {
        FIConfig.serverLang = "el_gr";
        assertEquals("floatingitems.one_second", FILang.getTranslations("floatingitems.one_second"));

        FIConfig.serverLang = "en_pt";
        assertEquals(
            "floatingitems.commands.cancel.usage",
            FILang.getTranslations("floatingitems.commands.cancel.usage"));
        assertEquals(
            "floatingitems.commands.cancel.display1",
            FILang.getTranslations("floatingitems.commands.cancel.display1"));
    }

    @Test
    public void constructedKeyIsTranslated() {
        String expected = translation("de_de", "floatingitems.update.display1");
        String key = new String("floatingitems.update.display1");
        FIConfig.serverLang = "de_de";
        assertEquals(expected, FILang.getTranslations(key));
    }

    @Test
    public void germanAlgeriaFallsBackToEnglish() {
        String english = translation("en_us", "floatingitems.update.display1");
        FIConfig.serverLang = "de_alg";
        assertEquals(english, FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void chineseTablesUseAsciiPlaceholders() {
        FIConfig.serverLang = "zh_cn";
        assertEquals(2, countPlaceholders(FILang.getTranslations("floatingitems.update.display1")));
        FIConfig.serverLang = "zh_tw";
        assertEquals(2, countPlaceholders(FILang.getTranslations("floatingitems.update.display3")));
    }

    @Test
    public void languageCodeLowercasingIsLocaleIndependent() {
        String italian = translation("it_it", "floatingitems.update.display1");
        Locale.setDefault(new Locale("tr", "TR"));
        FIConfig.serverLang = "IT_IT";
        assertEquals(italian, FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void enUdDoesNotUseServerStopText() {
        FIConfig.serverLang = "en_ud";
        String display1 = FILang.getTranslations("floatingitems.update.display1");
        String display3 = FILang.getTranslations("floatingitems.update.display3");
        assertFalse(
            display1.toLowerCase(Locale.ROOT)
                .contains("serverstop"));
        assertFalse(
            display3.toLowerCase(Locale.ROOT)
                .contains("serverstop"));
        assertEquals(2, countPlaceholders(display1));
        assertEquals(2, countPlaceholders(display3));
    }

    private static String translation(String language, String key) {
        FIConfig.serverLang = language;
        return FILang.getTranslations(key);
    }

    private static int countPlaceholders(String value) {
        int count = 0;
        for (int i = 0; i + 1 < value.length(); i++) {
            if (value.charAt(i) == '%' && value.charAt(i + 1) == 's') {
                count++;
            }
        }
        return count;
    }
}

package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

            // The upside-down locale intentionally renders the title in flipped glyphs; the other locales keep the
            // readable mod name.
            if (!"en_ud".equals(language)) {
                String title = FILang.getTranslations("fi.configgui.title");
                assertTrue(
                    "config title for " + language + " should contain the mod name",
                    title.contains(FIReference.MOD_NAME));
            }
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
        FIConfig.serverLang = "xx_xx";
        assertEquals(
            FILang.englishUSLang("floatingitems.update.display1"),
            FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void nullLanguageFallsBackToUsEnglish() {
        FIConfig.serverLang = null;
        assertEquals(
            FILang.englishUSLang("floatingitems.update.display1"),
            FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void languageCodeIsCaseInsensitive() {
        FIConfig.serverLang = "DE_DE";
        assertEquals(
            FILang.germanLang("floatingitems.update.display1"),
            FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    public void unknownKeyIsReturnedUnchanged() {
        FIConfig.serverLang = "en_us";
        assertEquals("floatingitems.does.not.exist", FILang.getTranslations("floatingitems.does.not.exist"));
    }

    @Test
    public void greekOneSecondKeyIsStillTranslated() {
        // This key is a leftover from the ServerStop code base, but it is present in the Greek table and should stay
        // testable until the dead-key cleanup in CODEBASE_ANALYSIS.md removes it.
        assertFalse("floatingitems.one_second".equals(FILang.greekLang("floatingitems.one_second")));
    }

    @Test
    public void pirateCommandKeysAreStillTranslated() {
        // Also a ServerStop leftover. Kept as a characterization test until the dead translations are deleted.
        assertFalse(
            "floatingitems.commands.cancel.usage".equals(FILang.englishPTLang("floatingitems.commands.cancel.usage")));
        assertFalse(
            "floatingitems.commands.cancel.display1"
                .equals(FILang.englishPTLang("floatingitems.commands.cancel.display1")));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-11: FILang compares keys with ==, so constructed keys are not translated")
    public void constructedKeyIsTranslated() {
        FIConfig.serverLang = "de_de";
        String key = new String("floatingitems.update.display1");
        assertEquals(FILang.germanLang("floatingitems.update.display1"), FILang.getTranslations(key));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-12: de_alg is not a Minecraft locale; remove or correct it")
    public void germanAlgeriaAliasIsNotNeeded() {
        // This test is a reminder rather than a behavioural assertion: the alias has no documented locale.
        FIConfig.serverLang = "de_alg";
        assertEquals(
            FILang.germanLang("floatingitems.update.display1"),
            FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-13: Chinese tables use the fullwidth percent sign, so %s is never substituted")
    public void chineseTablesUseAsciiPlaceholders() {
        FIConfig.serverLang = "zh_cn";
        assertEquals(2, countPlaceholders(FILang.getTranslations("floatingitems.update.display1")));
        FIConfig.serverLang = "zh_tw";
        assertEquals(2, countPlaceholders(FILang.getTranslations("floatingitems.update.display3")));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-16: toLowerCase() uses the default locale, so an uppercase locale code can miss")
    public void languageCodeLowercasingIsLocaleIndependent() {
        Locale.setDefault(new Locale("tr", "TR"));
        FIConfig.serverLang = "IT_IT";
        assertEquals(
            FILang.italianLang("floatingitems.update.display1"),
            FILang.getTranslations("floatingitems.update.display1"));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-14: en_ud is a copy of the ServerStop text and never mentions FloatingItems")
    public void upsideDownEnglishMentionsThisMod() {
        FIConfig.serverLang = "en_ud";
        assertEquals(
            "the en_ud update text should mention FloatingItems, not ServerStop",
            true,
            FILang.getTranslations("floatingitems.update.display1")
                .toLowerCase(Locale.ROOT)
                .contains("floatingitems"));
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

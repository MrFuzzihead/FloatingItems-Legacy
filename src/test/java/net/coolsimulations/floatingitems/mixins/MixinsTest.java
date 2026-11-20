package net.coolsimulations.floatingitems.mixins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizon.gtnhmixins.builders.AbstractBuilder;
import com.gtnewhorizon.gtnhmixins.builders.IBaseTransformer;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * Pins the mixin registration wiring: one early mixin, named {@code EntityItemMixin}, in the early phase.
 *
 * <p>
 * Calling {@code IMixins.getEarlyMixins} from a headless test would ask {@code FMLLaunchHandler} which physical side
 * is running, and that field is only set by the FML tweaker. The builder's protected metadata is read reflectively
 * instead, which is exactly the data GTNHMixins consumes.
 * </p>
 */
public class MixinsTest {

    @Test
    public void exactlyOneEarlyMixinIsDeclared() {
        assertEquals(1, Mixins.values().length);
        assertEquals("MINECRAFT", Mixins.values()[0].name());
    }

    @Test
    public void entityItemMixinIsRegisteredAsCommonEarlyContent() throws Exception {
        MixinBuilder builder = Mixins.MINECRAFT.getBuilder();
        assertNotNull(builder);

        assertEquals(IBaseTransformer.Phase.EARLY, read(builder, "phase"));

        @SuppressWarnings("unchecked")
        List<String> common = (List<String>) read(builder, "commonClasses");
        assertNotNull(common);
        assertTrue(common.contains("EntityItemMixin"));
        assertEquals(1, common.size());
    }

    @Test
    public void loaderPointsAtTheEarlyMixinConfig() {
        EarlyMixinsLoader loader = new EarlyMixinsLoader();
        assertEquals("mixins.floatingitems.early.json", loader.getMixinConfig());
        assertNotNull(loader.getASMTransformerClass());
        assertEquals(0, loader.getASMTransformerClass().length);
    }

    private static Object read(Object target, String fieldName) throws Exception {
        Field field = AbstractBuilder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

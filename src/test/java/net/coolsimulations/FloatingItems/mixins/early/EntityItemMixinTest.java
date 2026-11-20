package net.coolsimulations.FloatingItems.mixins.early;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import net.minecraft.entity.Entity;

import org.junit.Test;

/**
 * Unit-tests the pure motion helper in the mixin.
 *
 * <p>
 * The rest of {@link EntityItemMixin#onUpdate} needs a real, mixin-transformed {@code EntityItem} in a live
 * {@code World}; that is what the {@code gameTest} source set is for. The helper, however, is ordinary arithmetic
 * and is worth pinning down independently.
 * </p>
 */
public class EntityItemMixinTest {

    @Test
    public void underwaterDampingAppliesToHorizontalMotionAndAddsSlowLift() throws Exception {
        Entity entity = mock(Entity.class);
        entity.motionX = 1.0D;
        entity.motionY = 0.05D;
        entity.motionZ = -2.0D;

        invokeUnderwaterMovement(entity);

        assertEquals(0.95D, entity.motionX, 1.0E-12);
        assertEquals(0.0505D, entity.motionY, 1.0E-12);
        assertEquals(-1.9D, entity.motionZ, 1.0E-12);
    }

    @Test
    public void theSlowLiftIsNotAppliedAtOrAboveTheThreshold() throws Exception {
        Entity entity = mock(Entity.class);
        entity.motionY = 0.06D;

        invokeUnderwaterMovement(entity);

        assertEquals(0.06D, entity.motionY, 1.0E-12);
    }

    @Test
    public void negativeVerticalMotionIsLiftedTowardsZero() throws Exception {
        Entity entity = mock(Entity.class);
        entity.motionY = -0.5D;

        invokeUnderwaterMovement(entity);

        assertEquals(-0.4995D, entity.motionY, 1.0E-12);
    }

    private static void invokeUnderwaterMovement(Entity entity) throws Exception {
        Method method = EntityItemMixin.class.getDeclaredMethod("setUnderWaterMovement", Entity.class);
        method.setAccessible(true);
        method.invoke(null, entity);
    }
}

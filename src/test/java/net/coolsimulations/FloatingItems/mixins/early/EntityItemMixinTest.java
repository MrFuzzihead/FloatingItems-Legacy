package net.coolsimulations.FloatingItems.mixins.early;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit-tests the pure helpers in the mixin.
 *
 * <p>
 * The redirect itself needs a real, mixin-transformed {@code EntityItem} in a live {@code World}; that is what the
 * {@code gameTest} source set is for. The motion and water-box helpers are ordinary arithmetic/mockable logic and
 * are worth pinning down independently.
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

    @Test
    public void waterCheckUsesTheEntityBoundingBox() throws Exception {
        World world = mock(World.class);
        // Constructing with a real World would require a WorldProvider; the entity constructor only needs a
        // non-null world to read provider.dimensionId, so create it with null and attach the mock afterward.
        EntityItem item = new EntityItem(null);
        item.worldObj = world;
        item.setPosition(0.5D, 1.0D, 0.5D);
        when(world.isMaterialInBB(any(AxisAlignedBB.class), eq(Material.water))).thenReturn(true);

        assertTrue(invokeIsInWater(item));

        ArgumentCaptor<AxisAlignedBB> captor = ArgumentCaptor.forClass(AxisAlignedBB.class);
        verify(world).isMaterialInBB(captor.capture(), eq(Material.water));
        AxisAlignedBB checked = captor.getValue();
        assertEquals(item.boundingBox.minY + 0.001D, checked.minY, 1.0E-9D);
        assertEquals(item.boundingBox.maxY - 0.001D, checked.maxY, 1.0E-9D);
    }

    private static void invokeUnderwaterMovement(Entity entity) throws Exception {
        Method method = EntityItemMixin.class.getDeclaredMethod("setUnderWaterMovement", Entity.class);
        method.setAccessible(true);
        method.invoke(null, entity);
    }

    private static boolean invokeIsInWater(EntityItem entity) throws Exception {
        Method method = EntityItemMixin.class.getDeclaredMethod("isInWater", EntityItem.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, entity);
    }
}

package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * Covers the login notification guards. The update check is asynchronous, so either message can still be null when
 * a player joins; handing a null component to the player connection used to crash the login.
 */
public class FloatingItemsEventTest {

    @Before
    @After
    public void reset() {
        FIConfig.blacklist = FIConfig.Blacklist.empty();
        FIConfig.disableUpdateCheck = false;
        FIUpdateHandler.isOld = false;
        FIUpdateHandler.updateInfo = null;
        FIUpdateHandler.updateVersionInfo = null;
    }

    @Test
    public void postInitDropsUnresolvedBlacklistItems() {
        FIConfig.blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{99999}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        new FloatingItems().postInit(null);

        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .size());
    }

    @Test
    public void loginWithNoMessagesDoesNotAddAnything() {
        FIUpdateHandler.isOld = true;
        EntityPlayerMP player = mock(EntityPlayerMP.class);

        new FloatingItems().onPlayerLogin(new PlayerLoggedInEvent(player));

        verify(player, never()).addChatMessage(any(IChatComponent.class));
    }

    @Test
    public void loginWithOnlyTheVersionMessageAddsExactlyThatMessage() {
        FIUpdateHandler.isOld = true;
        FIUpdateHandler.updateVersionInfo = new ChatComponentText("1.2.3");
        EntityPlayerMP player = mock(EntityPlayerMP.class);

        new FloatingItems().onPlayerLogin(new PlayerLoggedInEvent(player));

        verify(player).addChatMessage(FIUpdateHandler.updateVersionInfo);
        verify(player, times(1)).addChatMessage(any(IChatComponent.class));
    }

    @Test
    public void loginWithBothMessagesAddsBoth() {
        FIUpdateHandler.isOld = true;
        FIUpdateHandler.updateInfo = new ChatComponentTranslation("floatingitems.update.display1", new Object[0]);
        FIUpdateHandler.updateVersionInfo = new ChatComponentText("1.2.3");
        EntityPlayerMP player = mock(EntityPlayerMP.class);

        new FloatingItems().onPlayerLogin(new PlayerLoggedInEvent(player));

        verify(player).addChatMessage(FIUpdateHandler.updateInfo);
        verify(player).addChatMessage(FIUpdateHandler.updateVersionInfo);
        verify(player, times(2)).addChatMessage(any(IChatComponent.class));
    }

    @Test
    public void loginDoesNothingWhenTheUpdateCheckerIsDisabled() {
        FIConfig.disableUpdateCheck = true;
        FIUpdateHandler.isOld = true;
        FIUpdateHandler.updateInfo = new ChatComponentTranslation("floatingitems.update.display1", new Object[0]);
        FIUpdateHandler.updateVersionInfo = new ChatComponentText("1.2.3");
        EntityPlayerMP player = mock(EntityPlayerMP.class);

        new FloatingItems().onPlayerLogin(new PlayerLoggedInEvent(player));

        verify(player, never()).addChatMessage(any(IChatComponent.class));
    }

    @Test
    public void loginDoesNothingWhenTheModIsCurrent() {
        FIUpdateHandler.isOld = false;
        FIUpdateHandler.updateInfo = new ChatComponentTranslation("floatingitems.update.display1", new Object[0]);
        FIUpdateHandler.updateVersionInfo = new ChatComponentText("1.2.3");
        EntityPlayerMP player = mock(EntityPlayerMP.class);

        new FloatingItems().onPlayerLogin(new PlayerLoggedInEvent(player));

        verify(player, never()).addChatMessage(any(IChatComponent.class));
    }
}

package art.arcane.react.nms.v26_3_R1;

import art.arcane.react.nms.BrewingTickHook;
import art.arcane.react.nms.BrewingTickResult;
import art.arcane.react.nms.FurnaceTickHook;
import art.arcane.react.nms.FurnaceTickResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickAdvanceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void removeHooks() {
        FurnaceTickRuntime.configure(null);
        BrewingTickRuntime.configure(null);
    }

    @Test
    void furnaceAdvancesCookAndFuelTimersWithoutSkippingCompletion() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        AbstractFurnaceBlockEntity furnace = Mockito.mock(AbstractFurnaceBlockEntity.class);
        furnace.cookingTotalTime = 200;
        furnace.cookingTimer = 196;
        furnace.litTimeRemaining = 2;
        FurnaceTickHook hook = (world, x, y, z) -> FurnaceTickResult.runAndAdvance(5);
        FurnaceTickRuntime.configure(hook);

        assertFalse(FurnaceTickRuntime.enter(level, BlockPos.ZERO, null, furnace));
        assertEquals(199, furnace.cookingTimer);
        assertEquals(1, furnace.litTimeRemaining);
    }

    @Test
    void brewingAdvancesTheRecipesRemainingTime() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        BrewingStandBlockEntity brewer = Mockito.mock(BrewingStandBlockEntity.class);
        brewer.brewTime = 73;
        BrewingTickHook hook = (world, x, y, z) -> BrewingTickResult.runAndAdvance(11);
        BrewingTickRuntime.configure(hook);

        assertFalse(BrewingTickRuntime.enter(level, BlockPos.ZERO, null, brewer));
        assertEquals(62, brewer.brewTime);
        brewer.brewTime = 3;
        assertFalse(BrewingTickRuntime.enter(level, BlockPos.ZERO, null, brewer));
        assertEquals(1, brewer.brewTime);
    }

    @Test
    void skippedBrewingPreservesTimersAndUninstallRestoresVanilla() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        BrewingStandBlockEntity brewer = Mockito.mock(BrewingStandBlockEntity.class);
        brewer.brewTime = 80;
        BrewingTickHook hook = (world, x, y, z) -> BrewingTickResult.SKIP;
        BrewingTickRuntime.configure(hook);

        assertTrue(BrewingTickRuntime.enter(level, BlockPos.ZERO, null, brewer));
        assertEquals(80, brewer.brewTime);
        BrewingTickRuntime.configure(null);
        assertFalse(BrewingTickRuntime.enter(level, BlockPos.ZERO, null, brewer));
        assertEquals(80, brewer.brewTime);
    }
}

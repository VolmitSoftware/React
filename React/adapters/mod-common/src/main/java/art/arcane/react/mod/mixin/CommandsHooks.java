package art.arcane.react.mod.mixin;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsHooks {
    @Inject(method = "performCommand", at = @At("HEAD"), require = 0)
    private void react$performing(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo callback) {
        SamplerEventCounters.get().count(Counter.COMMAND, null);
    }
}

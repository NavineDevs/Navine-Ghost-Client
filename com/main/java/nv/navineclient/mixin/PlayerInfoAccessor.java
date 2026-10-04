package nv.navineclient.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInfo.class)
public interface PlayerInfoAccessor {
    @Accessor("gameMode")
    void navine$setGameMode(GameType gameMode);
}

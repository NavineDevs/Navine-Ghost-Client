package nv.navineclient.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("keyPresses")
    @Mutable
    void navine$setKeyPresses(Input keyPresses);

    @Accessor("moveVector")
    @Mutable
    void navine$setMoveVector(Vec2 moveVector);
}

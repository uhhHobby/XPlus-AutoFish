package com.wudji.xplusautofish.mixin;

import com.wudji.xplusautofish.ForgeModXPlusAutofish;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPlayNetworkHandler {
    @Shadow Minecraft minecraft;

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    public void onPlaySound(ClientboundSoundPacket playSoundS2CPacket_1, CallbackInfo ci) {
        if (minecraft.isSameThread()) ForgeModXPlusAutofish.getInstance().handlePacket(playSoundS2CPacket_1);
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    public void onVelocityUpdate(ClientboundSetEntityMotionPacket entityVelocityUpdateS2CPacket_1, CallbackInfo ci) {
        if (minecraft.isSameThread()) ForgeModXPlusAutofish.getInstance().handlePacket(entityVelocityUpdateS2CPacket_1);
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    public void onSysChatMessage(ClientboundSystemChatPacket p_233708_, CallbackInfo ci) {
        if (minecraft.isSameThread()) ForgeModXPlusAutofish.getInstance().handleChat(p_233708_);
    }
}

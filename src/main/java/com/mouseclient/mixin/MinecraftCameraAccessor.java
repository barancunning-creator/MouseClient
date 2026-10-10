package com.mouseclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftCameraAccessor {
   public @Accessor("cameraEntity")
   void setSessionCameraEntity(Entity sessionCameraEntity);
}

package com.mouseclient.mixin;

import com.mouseclient.access.ChamsRenderState;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements ChamsRenderState {
   @Unique
   private boolean chams;

   @Override
   public boolean mouseclient$chams() {
      return this.chams;
   }

   @Override
   public void mouseclient$setChams(boolean chams) {
      this.chams = chams;
   }
}

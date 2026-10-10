package com.mouseclient.mixin;

import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.MovementFreezeController;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.render.FreeCamera;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

   @Inject(method = "tick()V", at = @At("TAIL"))
   private void stripFreeCameraKeys(CallbackInfo callback) {
      if (FreeCamera.getInstance().isEnabled()) {
         this.playerInput = PlayerInput.DEFAULT;
         this.movementVector = Vec2f.ZERO;
      }
   }

   @Inject(method = "tick()V", at = @At("TAIL"))
   private void stripFrozenMoveKeys(CallbackInfo callback) {
      if (MovementFreezeController.getInstance().isFrozen()) {
         this.playerInput = new PlayerInput(false, false, false, false, false, this.playerInput.sneak(), this.playerInput.sprint());
         this.movementVector = Vec2f.ZERO;
      }
   }

   @Inject(method = "tick()V", at = @At("TAIL"))
   private void emitMoveInput(CallbackInfo callback) {
      if (EventBus.getInstance().hasListeners(MovementInputEvent.class)) {
         MovementInputEvent event = new MovementInputEvent(
            this.playerInput,
            movementImpulse(this.playerInput.forward(), this.playerInput.backward()),
            movementImpulse(this.playerInput.left(), this.playerInput.right()),
            this.playerInput.jump(),
            this.playerInput.sneak()
         );
         EventBus.getInstance().post(event);
         if (!event.isCancelled()) {
            this.movementVector = new Vec2f(event.getStrafe(), event.getForward()).normalize();
            this.playerInput = new PlayerInput(
               event.getForward() > 0.0F,
               event.getForward() < 0.0F,
               event.getStrafe() > 0.0F,
               event.getStrafe() < 0.0F,
               event.isJumping(),
               event.isSneaking(),
               event.getInput().sprint()
            );
         }
      }
   }

   @Unique
   private static float movementImpulse(boolean positive, boolean negative) {
      return positive == negative ? 0.0F : (positive ? 1.0F : (-1.0F));
   }

   static {
      VMBridge.identifyClass(KeyboardInputMixin.class, "C9cYwOv6");
   }
}

package com.mouseclient.modules.impl.combat;

import com.mouseclient.modules.impl.movement.CancelableEvent;
import lombok.Generated;
import net.minecraft.util.PlayerInput;

public final class MovementInputEvent extends CancelableEvent {
   private PlayerInput playerInput;
   private float forward;
   private float strafe;
   private boolean jumping;
   private boolean sneaking;
   private boolean movementCorrected;

   public boolean isMovementCorrected() {
      return this.movementCorrected;
   }

   public void setMovementCorrected(boolean movementCorrected) {
      this.movementCorrected = movementCorrected;
   }

   @Generated
   public MovementInputEvent(PlayerInput playerInput, float forward, float strafe, boolean jumping, boolean sneaking) {
      this.playerInput = playerInput;
      this.forward = forward;
      this.strafe = strafe;
      this.jumping = jumping;
      this.sneaking = sneaking;
   }

   @Generated
   public boolean isSneaking() {
      return this.sneaking;
   }

   @Generated
   public float getForward() {
      return this.forward;
   }

   @Generated
   public void setJumping(boolean jumping) {
      this.jumping = jumping;
   }

   @Generated
   public PlayerInput getInput() {
      return this.playerInput;
   }

   @Generated
   public void setInput(PlayerInput input) {
      this.playerInput = input;
   }

   @Generated
   public boolean isJumping() {
      return this.jumping;
   }

   @Generated
   public void setForward(float forward) {
      this.forward = forward;
   }

   @Generated
   public float getStrafe() {
      return this.strafe;
   }

   @Generated
   public void setSneaking(boolean sneaking) {
      this.sneaking = sneaking;
   }

   @Generated
   public void setStrafe(float strafe) {
      this.strafe = strafe;
   }
}

package com.mouseclient.util.entity;

import net.minecraft.util.math.Vec3d;

public interface PlayerSimulation {
   public Vec3d sourcePosition();

   public void tick();
}

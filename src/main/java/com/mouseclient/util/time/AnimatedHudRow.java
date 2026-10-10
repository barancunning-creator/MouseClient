package com.mouseclient.util.time;

public abstract class AnimatedHudRow {
   public final TweenAnimation visibilityAnimation = new TweenAnimation();
   public boolean visible;
   public float rowX;
   public int rowIndex;
   public boolean unusedFlag;
}

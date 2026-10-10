package com.mouseclient.util.time;

@FunctionalInterface
public interface EasingFunction {
   public double apply(double progress);
}

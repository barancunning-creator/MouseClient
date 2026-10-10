package com.mouseclient.render.avatar;

import com.mouseclient.util.time.EasingFunction;
import lombok.Generated;

public final class Easings {
   public static final double BACK_OVERSHOOT = 1.70158;
   public static final double BACK_IN_OUT_OVERSHOOT = 2.5949095;
   public static final double BACK_CUBIC_FACTOR = 2.70158;
   public static final double ELASTIC_PERIOD_FACTOR = Math.PI * 2.0 / 3.0;
   public static final double ELASTIC_IN_OUT_PERIOD_FACTOR = Math.PI * 4.0 / 9.0;
   public static final EasingFunction LINEAR = progress -> progress;
   public static final EasingFunction QUAD_IN = powerIn(2);
   public static final EasingFunction QUAD_OUT = powerOut(2);
   public static final EasingFunction QUAD_IN_OUT = powerInOut((2.0D));
   public static final EasingFunction CUBIC_IN = powerIn(3);
   public static final EasingFunction CUBIC_OUT = powerOut(3);
   public static final EasingFunction CUBIC_IN_OUT = powerInOut((3.0D));
   public static final EasingFunction QUART_IN = powerIn(4);
   public static final EasingFunction QUART_OUT = powerOut(4);
   public static final EasingFunction QUART_IN_OUT = powerInOut((4.0D));
   public static final EasingFunction QUINT_IN = powerIn(5);
   public static final EasingFunction QUINT_OUT = powerOut(5);
   public static final EasingFunction QUINT_IN_OUT = powerInOut((5.0D));
   public static final EasingFunction SINE_IN = progress -> 1.0 - Math.cos(progress * (3.141592653589793D) / (2.0D));
   public static final EasingFunction SINE_OUT = progress -> Math.sin(progress * (3.141592653589793D) / (2.0D));
   public static final EasingFunction SINE_IN_OUT = progress -> -(Math.cos((3.141592653589793D) * progress) - 1.0) / (2.0D);
   public static final EasingFunction CIRC_IN = progress -> 1.0 - Math.sqrt(1.0 - Math.pow(progress, (2.0D)));
   public static final EasingFunction CIRC_OUT = progress -> Math.sqrt(1.0 - Math.pow(progress - 1.0, (2.0D)));
   public static final EasingFunction CIRC_IN_OUT = progress -> progress < (0.5D)
      ? (1.0 - Math.sqrt(1.0 - Math.pow((2.0D) * progress, (2.0D)))) / (2.0D)
      : (Math.sqrt(1.0 - Math.pow((-2.0D) * progress + (2.0D), (2.0D))) + 1.0)
         / (2.0D);
   public static final EasingFunction ELASTIC_IN = progress -> progress != 0.0 && progress != 1.0
      ? Math.pow((-2.0D), (10.0D) * progress - (10.0D))
         * Math.sin((progress * (10.0D) - (10.75D)) * (2.0943951023931953D))
      : progress;
   public static final EasingFunction ELASTIC_OUT = progress -> progress != 0.0 && progress != 1.0
      ? Math.pow((2.0D), (-10.0D) * progress)
            * Math.sin((progress * (10.0D) - (0.75D)) * (2.0943951023931953D))
         + 1.0
      : progress;
   public static final EasingFunction ELASTIC_IN_OUT = progress -> {
      if (progress != 0.0 && progress != 1.0) {
         return progress < (0.5D)
            ? -(
                  Math.pow((2.0D), (20.0D) * progress - (10.0D))
                     * Math.sin(((20.0D) * progress - (11.125D)) * (1.3962634015954636D))
               )
               / (2.0D)
            : Math.pow((2.0D), (-20.0D) * progress + (10.0D))
                  * Math.sin(((20.0D) * progress - (11.125D)) * (1.3962634015954636D))
                  / (2.0D)
               + 1.0;
      } else {
         return progress;
      }
   };
   public static final EasingFunction EXPO_IN = progress -> progress != 0.0
      ? Math.pow((2.0D), (10.0D) * progress - (10.0D))
      : progress;
   public static final EasingFunction EXPO_OUT = progress -> progress != 1.0 ? 1.0 - Math.pow((2.0D), (-10.0D) * progress) : progress;
   public static final EasingFunction EXPO_IN_OUT = progress -> {
      if (progress != 0.0 && progress != 1.0) {
         return progress < (0.5D)
            ? Math.pow((2.0D), (20.0D) * progress - (10.0D)) / (2.0D)
            : (
                  (2.0D)
                     - Math.pow((2.0D), (-20.0D) * progress + (10.0D))
               )
               / (2.0D);
      } else {
         return progress;
      }
   };
   public static final EasingFunction BACK_IN = progress -> (2.70158D) * Math.pow(progress, (3.0D))
      - (1.70158D) * Math.pow(progress, (2.0D));
   public static final EasingFunction BACK_OUT = progress -> 1.0
      + (2.70158D) * Math.pow(progress - 1.0, (3.0D))
      + (1.70158D) * Math.pow(progress - 1.0, (2.0D));
   public static final EasingFunction BACK_IN_OUT = progress -> progress < (0.5D)
      ? Math.pow((2.0D) * progress, (2.0D))
         * ((7.189819D) * progress - (2.5949095D))
         / (2.0D)
      : (
            Math.pow((2.0D) * progress - (2.0D), (2.0D))
                  * ((3.5949095D) * (progress * (2.0D) - (2.0D)) + (2.5949095D))
               + (2.0D)
         )
         / (2.0D);
   public static final EasingFunction BOUNCE_OUT = progress -> {
      double bounceCoefficient = (7.5625D);
      double bounceDivisor = (2.75D);
      if (progress < 1.0 / bounceDivisor) {
         return bounceCoefficient * Math.pow(progress, (2.0D));
      } else if (progress < (2.0D) / bounceDivisor) {
         return bounceCoefficient * Math.pow(progress - (1.5D) / bounceDivisor, (2.0D)) + (0.75D);
      } else {
         return progress < (2.5D) / bounceDivisor
            ? bounceCoefficient * Math.pow(progress - (2.25D) / bounceDivisor, (2.0D)) + (0.9375D)
            : bounceCoefficient * Math.pow(progress - (2.625D) / bounceDivisor, (2.0D)) + (0.984375D);
      }
   };
   public static final EasingFunction BOUNCE_IN = progress -> 1.0 - BOUNCE_OUT.apply(1.0 - progress);
   public static final EasingFunction BOUNCE_IN_OUT = progress -> progress < (0.5D)
      ? (1.0 - BOUNCE_OUT.apply(1.0 - (2.0D) * progress)) / (2.0D)
      : (1.0 + BOUNCE_OUT.apply((2.0D) * progress - 1.0)) / (2.0D);

   public static EasingFunction powerOut(double exponent) {
      return progress -> 1.0 - Math.pow(1.0 - progress, exponent);
   }

   public static EasingFunction powerIn(int exponent) {
      return powerIn((double)exponent);
   }

   public static EasingFunction powerOut(int exponent) {
      return powerOut((double)exponent);
   }

   public static EasingFunction powerIn(double exponent) {
      return progress -> Math.pow(progress, exponent);
   }

   @Generated
   private Easings() {
      throw new UnsupportedOperationException(("This is a utility class and cannot be instantiated"));
   }

   public static EasingFunction powerInOut(double exponent) {
      return progress -> progress < (0.5D)
         ? Math.pow((2.0D), exponent - 1.0) * Math.pow(progress, exponent)
         : 1.0 - Math.pow((-2.0D) * progress + (2.0D), exponent) / (2.0D);
   }
}

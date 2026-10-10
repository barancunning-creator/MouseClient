package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.player.UseTracker;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class ArrowCountHud {
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static ItemStack displayedArrow = Items.ARROW.getDefaultStack();
   private static int arrowCount;

   private ArrowCountHud() {
   }

   public static void render(float screenWidth, float screenHeight) {
      UseTracker useTracker = UseTracker.getInstance();
      boolean hasArrows = useTracker.arrowCount() > 0;
      if (hasArrows) {
         displayedArrow = useTracker.displayedArrow();
         arrowCount = useTracker.arrowCount();
      } else if (HudRenderContext.isChatVisible()) {
         displayedArrow = Items.ARROW.getDefaultStack();
         arrowCount = 3;
      }

      visibilityAnimation.update();
      visibilityAnimation.animateTo(!hasArrows && !HudRenderContext.isChatVisible() ? 0.0 : 1.0, (0.4D), Easings.QUART_OUT);
      float alpha = visibilityAnimation.floatValue();
      String countText = String.valueOf(arrowCount);
      float width = (13.0F) + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, countText, (7.0F));
      float height = (10.0F);
      float[] anchor = HudLayout.resolveAnchor(("arrows"), screenWidth / 2.0F - width / 2.0F, screenHeight / 2.0F + (18.0F));
      HudLayout.registerBounds(("arrows"), anchor[0], anchor[1], width, height);
      if (!(alpha <= (0.001F))) {
         HudRenderContext.pushIdentityScale(anchor[0] + width / 2.0F, anchor[1] + height / 2.0F, alpha);
         GuiItemRenderer.render(displayedArrow, Math.round(anchor[0] * 2.0F) / 2.0F, Math.round(anchor[1] * 2.0F) / 2.0F, (10.0F), ItemRenderOptions.noDecorations(alpha));
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, countText, anchor[0] + (13.0F), anchor[1] + (1.5F), (7.0F), HudPanelRenderer.primaryTextColor(alpha));
         HudRenderContext.popTransform();
      }
   }
}

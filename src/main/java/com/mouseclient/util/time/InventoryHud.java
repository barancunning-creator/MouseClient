package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public final class InventoryHud {
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();

   public static void render(MinecraftClient client, float x, float y) {
      if (client.player != null) {
         boolean hasItems = false;

         for (int inventorySlot = 9; inventorySlot < 36 && !hasItems; inventorySlot++) {
            hasItems = !client.player.getInventory().getStack(inventorySlot).isEmpty();
         }

         visibilityAnimation.update();
         visibilityAnimation.animateTo(!hasItems && !HudRenderContext.isChatVisible() ? 0.0 : 1.0, (0.4D), Easings.QUART_OUT);
         float alpha = visibilityAnimation.floatValue();
         if (!(alpha <= (0.001F))) {
            float width = Math.max((16.0F) + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("Inventory"), (6.0F)) + (4.0F), (111.0F));
            float height = (51.0F);
            float[] anchor = HudLayout.resolveAnchor(("inventory"), x, y);
            HudLayout.registerBounds(("inventory"), anchor[0], anchor[1], width, height);
            HudRenderContext.pushIdentityScale(anchor[0] + width / 2.0F, anchor[1] + height / 2.0F, alpha);
            HudPanelRenderer.drawHeader(anchor[0], anchor[1], width, alpha, HudIcon.INVENTORY, ("Inventory"));
            float rowX = anchor[0] + (width - (111.0F)) / 2.0F;
            float rowsY = anchor[1] + (13.0F) + 2.0F;

            for (int row = 0; row < 3; row++) {
               float rowY = rowsY + row * (12.5F);
               HudPanelRenderer.drawPanel(rowX, rowY, (111.0F), (11.0F), alpha);
               float dividerY = rowY + (5.5F);

               for (int dividerIndex = 0; dividerIndex < 8; dividerIndex++) {
                  HudPanelRenderer.drawDivider(rowX + dividerIndex * (12.5F) + (11.0F) + (0.75F) - (0.25F), dividerY, alpha, (1.5F));
               }

               for (int column = 0; column < 9; column++) {
                  ItemStack stack = client.player.getInventory().getStack(9 + row * 9 + column);
                  if (!stack.isEmpty()) {
                     float itemX = Math.round((rowX + column * (12.5F) + (1.5F)) * 2.0F) / 2.0F;
                     float itemY = Math.round((rowY + (1.5F)) * 2.0F) / 2.0F;
                     GuiItemRenderer.render(stack, itemX, itemY, (8.0F), ItemRenderOptions.decorated(alpha));
                  }
               }
            }

            HudRenderContext.popTransform();
         }
      }
   }
}

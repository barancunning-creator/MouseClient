package com.mouseclient.mixin;

import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.ClientCommand;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
   public @Shadow
   @Final
   TextFieldWidget textField;
   @Shadow
   @Final
   private List<OrderedText> messages;
   public @Shadow
   boolean completingSuggestions;
   @Shadow
   private CompletableFuture<Suggestions> pendingSuggestions;

   @Shadow
   public abstract void show(boolean narrateFirstSuggestion);

   @Shadow
   public abstract boolean isOpen();

   @Shadow
   public abstract void clearWindow();

   @Inject(method = "refresh()V", at = @At("HEAD"), cancellable = true)
   private void suggestClientCommands(CallbackInfo callback) {
      if (ClientInitializer.inst().commandDispatcher() != null
         && this.textField.getText().startsWith(ClientInitializer.inst().commandDispatcher().prefix())) {
         if (this.isOpen() && this.completingSuggestions) {
            callback.cancel();
         } else {
            this.textField.setSuggestion(null);
            this.messages.clear();
            SuggestionsBuilder suggestionsBuilder = new SuggestionsBuilder(this.textField.getText(), this.textField.getText().lastIndexOf(32) + 1);
            if (this.textField.getText().contains((" "))) {
               for (String suggestion : ClientInitializer.inst().commandDispatcher().suggestions(this.textField.getText())) {
                  suggestionsBuilder.suggest(suggestion);
               }
            } else {
               for (ClientCommand command : ClientInitializer.inst().commandDispatcher().matchingCommands(this.textField.getText())) {
                  suggestionsBuilder.suggest(ClientInitializer.inst().commandDispatcher().prefix() + command.name(), Text.literal(command.description()));
               }
            }

            this.pendingSuggestions = suggestionsBuilder.buildFuture();
            this.clearWindow();
            this.show(false);
            callback.cancel();
         }
      }
   }

   static {
      VMBridge.identifyClass(ChatInputSuggestorMixin.class, "l35ylYf5");
   }
}

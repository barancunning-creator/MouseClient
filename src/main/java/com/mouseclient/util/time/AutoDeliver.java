package com.mouseclient.util.time;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.FilteredTextSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "AutoDeliver", description = "Автоматическая выдача валюты по заказам FunPay.", category = ModuleCategory.MISC)
public final class AutoDeliver extends Module {
   private static AutoDeliver INSTANCE;
   public FilteredTextSetting goldenKey;
   public FilteredTextSetting pricePerMillion;
   public DecimalSetting sellAboveMillion;
   public FilteredTextSetting deliveryAnarchy;
   private static final Pattern APP_DATA_PATTERN = Pattern.compile(("data-app-data=\"([^\"]*)\""));
   private static final Pattern FORM_ACTION_PATTERN = Pattern.compile(("<form[^>]*action=\"([^\"]*)\""));
   private static final Pattern INPUT_TAG_PATTERN = Pattern.compile(("<input[^>]*>"));
   private static final Pattern ORDER_UNITS_PATTERN = Pattern.compile(("(\\d+)\\s*шт\\."));
   private static final Pattern ANARCHY_TITLE_PATTERN = Pattern.compile(("Анархия-(\\d+)"));
   private final HttpClient httpClient;
   private ScheduledExecutorService pollExecutor;
   private String marketplaceUserId;
   private String csrfToken;
   private final HashMap<String, String> sessionCookies;
   private String conversationTag;
   private ArrayList<AutoDeliver.BuyerConversation> conversations;
   private ArrayList<AutoDeliver.PaidOrder> paidOrders;
   private final ArrayList<String> seenOrderIds;
   private final ConcurrentLinkedQueue<AutoDeliver.DeliveryRequest> deliveryQueue;
   private long lastGameActionAt;
   private long lastAnarchySwitchAt;
   private boolean idleHubSent;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<PacketEvent> packetListener;

   private void sendBuyerMessage(String conversationId, String message) {
      if (conversationId != null && !conversationId.isEmpty()) {
         try {
            JsonObject json = new JsonObject();
            json.addProperty(("node"), conversationId);
            json.addProperty(("last_message"), -1);
            json.addProperty(("content"), message);
            JsonObject messageRequest = new JsonObject();
            messageRequest.addProperty(("action"), ("chat_message"));
            messageRequest.add(("data"), json);
            JsonObject nodeRequest = new JsonObject();
            nodeRequest.addProperty(("type"), ("chat_node"));
            nodeRequest.addProperty(("id"), conversationId);
            nodeRequest.addProperty(("tag"), ("00000000"));
            JsonObject nodeData = new JsonObject();
            nodeData.addProperty(("node"), conversationId);
            nodeData.addProperty(("last_message"), -1);
            nodeData.addProperty(("content"), "");
            nodeRequest.add(("data"), nodeData);
            JsonArray jsonArray = new JsonArray();
            jsonArray.add(nodeRequest);
            if (this.runnerSucceeded(this.sendRunnerRequest(jsonArray.toString(), messageRequest.toString()))) {
               return;
            }

            this.marketplaceUserId = null;
            if (this.ensureSession()) {
               this.sendRunnerRequest(jsonArray.toString(), messageRequest.toString());
            }
         } catch (Exception error) {
         }
      }
   }

   private boolean runnerSucceeded(HttpResponse<String> response) {
      if (response.statusCode() != 200) {
         return false;
      }

      try {
         JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject();
         if (json.has(("error")) && !json.get(("error")).isJsonNull()) {
            return false;
         } else {
            return json.has(("response")) && json.get(("response")).isJsonObject()
               ? !json.getAsJsonObject(("response")).has(("error")) || json.getAsJsonObject(("response")).get(("error")).isJsonNull()
               : true;
         }
      } catch (Exception error) {
         return false;
      }
   }

   private boolean matchesUnitPrice(double unitPrice) {
      if (this.pricePerMillion.getText().isBlank()) {
         return true;
      }

      try {
         double configuredUnitPrice = Double.parseDouble(this.pricePerMillion.getText().replace(',', '.').replaceAll(("[^0-9.]"), ""));
         return configuredUnitPrice <= 0.0 || Math.abs(unitPrice - configuredUnitPrice) <= configuredUnitPrice * (0.25D);
      } catch (Exception error) {
         return true;
      }
   }

   private String htmlAttribute(String html, String attribute) {
      Matcher attributeMatcher = Pattern.compile(attribute + "=\"([^\"]*)\"").matcher(html);
      return attributeMatcher.find() ? attributeMatcher.group(1) : "";
   }

   private String cookieHeader() {
      StringBuilder textBuilder = new StringBuilder(("golden_key=")).append(this.goldenKey.getText());

      for (String cookieName : this.sessionCookies.keySet()) {
         textBuilder.append(("; ")).append(cookieName).append('=').append(this.sessionCookies.get(cookieName));
      }

      return textBuilder.toString();
   }

   @Override
   protected void onEnable() {
      if (this.goldenKey.getText().isBlank()) {
         ChatMessages.printPrefixed(("Укажите Golden-Key в настройках AutoDeliver"));
         this.toggle();
      } else {
         this.marketplaceUserId = null;
         this.conversations = null;
         this.paidOrders = null;
         this.seenOrderIds.clear();
         this.deliveryQueue.clear();
         this.idleHubSent = false;
         this.pollExecutor = Executors.newSingleThreadScheduledExecutor(pollTask -> {
            Thread pollThread = new Thread(pollTask, ("mouseclient-funpay-deliver"));
            pollThread.setDaemon(true);
            return pollThread;
         });
         this.pollExecutor.scheduleWithFixedDelay(this::pollMarketplace, 0L, (4L), TimeUnit.SECONDS);
      }
   }

   private boolean ensureSession() {
      if (this.marketplaceUserId != null) {
         return true;
      }

      try {
         this.sessionCookies.clear();
         HttpResponse homepageResponse = this.httpClient
            .send(
               HttpRequest.newBuilder(URI.create(("https://funpay.com/")))
                  .timeout(Duration.ofSeconds((5L)))
                  .header(("cookie"), "golden_key=" + this.goldenKey.getText())
                  .GET()
                  .build(),
               BodyHandlers.ofString()
            );
         if (this.textByHtmlClass((String)homepageResponse.body(), ("user-link-name")).isEmpty()) {
            return false;
         }

         Matcher appDataMatcher = APP_DATA_PATTERN.matcher((CharSequence)homepageResponse.body());
         if (!appDataMatcher.find()) {
            return false;
         }

         JsonObject json = JsonParser.parseString(this.decodeHtmlEntities(appDataMatcher.group(1))).getAsJsonObject();
         this.marketplaceUserId = json.get(("userId")).getAsString();
         this.csrfToken = json.get(("csrf-token")).getAsString();
         this.captureResponseCookies(homepageResponse);
         this.conversations = null;
         return true;
      } catch (Exception error) {
         return false;
      }
   }

   private void pollMarketplace() {
      try {
         if (!this.ensureSession()) {
            return;
         }

         this.refreshConversations();
         this.refreshPaidOrders();
      } catch (Exception error) {
         this.marketplaceUserId = null;
      }
   }

   @Override
   protected void onDisable() {
      if (this.pollExecutor != null) {
         this.pollExecutor.shutdownNow();
      }

      this.pollExecutor = null;
      this.deliveryQueue.clear();
   }

   private ArrayList<AutoDeliver.PaidOrder> fetchPaidOrders() {
      ArrayList orders = new ArrayList();

      try {
         HttpResponse ordersResponse = this.httpClient
            .send(
               HttpRequest.newBuilder(URI.create(("https://funpay.com/orders/trade")))
                  .timeout(Duration.ofSeconds((5L)))
                  .header(("cookie"), this.cookieHeader())
                  .header(("accept"), ("*/*"))
                  .header(("x-requested-with"), ("XMLHttpRequest"))
                  .POST(BodyPublishers.noBody())
                  .build(),
               BodyHandlers.ofString()
            );
         this.captureResponseCookies(ordersResponse);
         String ordersHtml = (String)ordersResponse.body();

         for (String orderHtml : this.splitHtmlByClass(ordersHtml, ("tc-item"))) {
            try {
               String descriptionSection = orderHtml.contains(("order-desc")) ? orderHtml.substring(orderHtml.indexOf(("order-desc"))) : "";
               if (this.textByHtmlClass(orderHtml, ("tc-status")).equalsIgnoreCase(("Оплачен")) && this.textByHtmlClass(descriptionSection, ("text-muted")).contains(("Валюта"))) {
                  String description = this.textByHtmlClass(orderHtml, ("order-desc"));
                  int units = 1;
                  Matcher unitsMatcher = ORDER_UNITS_PATTERN.matcher(description);

                  while (unitsMatcher.find()) {
                     units = Integer.parseInt(unitsMatcher.group(1));
                  }

                  double totalPrice = Double.parseDouble(this.textByHtmlClass(orderHtml, ("tc-price")).replace(',', '.').replaceAll(("[^0-9.]"), ""));
                  if (this.matchesUnitPrice(totalPrice / units)) {
                     String buyerLogin = this.textByHtmlClass(orderHtml, ("media-user-name"));
                     if (!buyerLogin.isEmpty()) {
                        orders.add(new AutoDeliver.PaidOrder(this.textByHtmlClass(orderHtml, ("tc-order")), buyerLogin, totalPrice, units));
                     }
                  }
               }
            } catch (Exception error) {
            }
         }

         return orders;
      } catch (Exception requestError) {
         return null;
      }
   }

   private void captureResponseCookies(HttpResponse<String> response) {
      for (String cookieHeaderValue : response.headers().allValues(("set-cookie"))) {
         String cookiePair = cookieHeaderValue.split((";"), 2)[0].trim();
         if (cookiePair.indexOf(61) > 0 && !cookiePair.startsWith(("golden_key="))) {
            this.sessionCookies.put(cookiePair.substring(0, cookiePair.indexOf(61)), cookiePair.substring(cookiePair.indexOf(61) + 1));
         }
      }
   }

   private int configuredAnarchy() {
      String digits = this.deliveryAnarchy.getText().replaceAll(("\\D"), "");
      return digits.isEmpty() ? -1 : Integer.parseInt(digits);
   }

   private ArrayList<AutoDeliver.BuyerConversation> fetchConversations() throws Exception {
      JsonObject json = new JsonObject();
      json.addProperty(("type"), ("chat_bookmarks"));
      json.addProperty(("id"), this.marketplaceUserId);
      json.addProperty(("tag"), this.conversationTag);
      json.addProperty(("data"), false);
      JsonArray jsonArray = new JsonArray();
      jsonArray.add(json);
      ArrayList fetchedConversations = new ArrayList();

      for (JsonElement jsonElement : JsonParser.parseString(this.sendRunnerRequest(jsonArray.toString(), String.valueOf(false)).body()).getAsJsonObject().getAsJsonArray(("objects"))) {
         JsonObject bookmarkObject = jsonElement.getAsJsonObject();
         if (("chat_bookmarks").equalsIgnoreCase(bookmarkObject.get(("type")).getAsString())) {
            this.conversationTag = bookmarkObject.get(("tag")).getAsString();

            for (String contactHtml : this.splitHtmlByClass(bookmarkObject.getAsJsonObject(("data")).get(("html")).getAsString(), ("contact-item"))) {
               AutoDeliver.BuyerConversation conversation = new AutoDeliver.BuyerConversation();
               conversation.id = this.htmlAttribute(contactHtml, ("data-id"));
               conversation.latestMessageId = this.htmlAttribute(contactHtml, ("data-node-msg")).isEmpty() ? 0L : Long.parseLong(this.htmlAttribute(contactHtml, ("data-node-msg")));
               conversation.buyerLogin = this.textByHtmlClass(contactHtml, ("media-user-name"));
               if (!conversation.id.isEmpty() && !conversation.buyerLogin.isEmpty()) {
                  fetchedConversations.add(conversation);
               }
            }
         }
      }

      return fetchedConversations;
   }

   private ArrayList<String> splitHtmlByClass(String html, String className) {
      ArrayList sections = new ArrayList();
      Matcher classMatcher = this.htmlClassPattern(className).matcher(html);

      int sectionStart;
      for (sectionStart = -1; classMatcher.find(); sectionStart = Math.max(html.lastIndexOf(60, classMatcher.start()), 0)) {
         if (sectionStart != -1) {
            sections.add(html.substring(sectionStart, classMatcher.start()));
         }
      }

      if (sectionStart != -1) {
         sections.add(html.substring(sectionStart));
      }

      return sections;
   }

   private String decodeHtmlEntities(String html) {
      return html.replace(("&quot;"), ("\""))
         .replace(("&#039;"), ("'"))
         .replace(("&apos;"), ("'"))
         .replace(("&lt;"), ("<"))
         .replace(("&gt;"), (">"))
         .replace(("&nbsp;"), (" "))
         .replace((" "), (" "))
         .replace(("&amp;"), ("&"));
   }

   private void refundOrder(String orderId) {
      try {
         HttpResponse orderResponse = this.httpClient
            .send(
               HttpRequest.newBuilder(URI.create("https://funpay.com/orders/" + (orderId.startsWith(("#")) ? orderId.substring(1) : orderId) + "/"))
                  .timeout(Duration.ofSeconds((5L)))
                  .header(("cookie"), this.cookieHeader())
                  .GET()
                  .build(),
               BodyHandlers.ofString()
            );
         this.captureResponseCookies(orderResponse);
         String orderHtml = (String)orderResponse.body();
         if (!orderHtml.contains(("modal-refund"))) {
            return;
         }

         String refundSection = orderHtml.substring(orderHtml.indexOf(("modal-refund")));
         Matcher actionMatcher = FORM_ACTION_PATTERN.matcher(refundSection);
         if (!actionMatcher.find()) {
            return;
         }

         String formHtml = refundSection.contains(("</form>")) ? refundSection.substring(0, refundSection.indexOf(("</form>"))) : refundSection;
         StringBuilder textBuilder = new StringBuilder();
         Matcher inputMatcher = INPUT_TAG_PATTERN.matcher(formHtml);

         while (inputMatcher.find()) {
            if (inputMatcher.group().contains(("type=\"hidden\""))) {
               textBuilder.append(URLEncoder.encode(this.htmlAttribute(inputMatcher.group(), ("name")), StandardCharsets.UTF_8))
                  .append('=')
                  .append(URLEncoder.encode(this.htmlAttribute(inputMatcher.group(), ("value")), StandardCharsets.UTF_8))
                  .append('&');
            }
         }

         textBuilder.append(("csrf_token=")).append(URLEncoder.encode(this.csrfToken == null ? "" : this.csrfToken, StandardCharsets.UTF_8));
         this.httpClient
            .send(
               HttpRequest.newBuilder(URI.create(actionMatcher.group(1).startsWith(("http")) ? actionMatcher.group(1) : "https://funpay.com" + actionMatcher.group(1)))
                  .timeout(Duration.ofSeconds((5L)))
                  .header(("cookie"), this.cookieHeader())
                  .header(("accept"), ("*/*"))
                  .header(("content-type"), ("application/x-www-form-urlencoded; charset=UTF-8"))
                  .header(("x-requested-with"), ("XMLHttpRequest"))
                  .POST(BodyPublishers.ofString(textBuilder.toString(), StandardCharsets.UTF_8))
                  .build(),
               BodyHandlers.ofString()
            );
      } catch (Exception error) {
      }
   }

   private int currentAnarchy() {
      if (minecraftClient.world == null) {
         return -1;
      }

      ScoreboardObjective sidebarObjective = minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
      if (sidebarObjective == null) {
         return -1;
      }

      Matcher anarchyMatcher = ANARCHY_TITLE_PATTERN.matcher(sidebarObjective.getDisplayName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""));
      return anarchyMatcher.find() ? Integer.parseInt(anarchyMatcher.group(1)) : -1;
   }

   private void refreshConversations() throws Exception {
      java.util.ArrayList<com.mouseclient.util.time.AutoDeliver.BuyerConversation> latestConversations = this.fetchConversations();
      if (!latestConversations.isEmpty()) {
         java.util.ArrayList<com.mouseclient.util.time.AutoDeliver.BuyerConversation> previousConversations = this.conversations;
         this.conversations = latestConversations;
         if (previousConversations != null) {
            for (AutoDeliver.BuyerConversation conversation : latestConversations) {
               AutoDeliver.BuyerConversation previousConversation = previousConversations.stream().filter(buyerConversation -> buyerConversation.id.equals(conversation.id)).findFirst().orElse(null);
               long previousMessageId = previousConversation != null ? previousConversation.latestMessageId : 0L;
               if (previousConversation != null) {
                  conversation.phase = previousConversation.phase;
                  conversation.nickname = previousConversation.nickname;
                  conversation.deliveryStartedAt = previousConversation.deliveryStartedAt;
               }

               if (conversation.latestMessageId > previousMessageId) {
                  JsonObject json = new JsonObject();
                  json.addProperty(("type"), ("chat_node"));
                  json.addProperty(("id"), conversation.id);
                  json.addProperty(("tag"), ("00000000"));
                  JsonObject nodeData = new JsonObject();
                  nodeData.addProperty(("node"), conversation.id);
                  nodeData.addProperty(("last_message"), -1);
                  nodeData.addProperty(("content"), "");
                  json.add(("data"), nodeData);
                  JsonArray jsonArray = new JsonArray();
                  jsonArray.add(json);

                  for (JsonElement jsonElement : JsonParser.parseString(this.sendRunnerRequest(jsonArray.toString(), String.valueOf(false)).body())
                     .getAsJsonObject()
                     .getAsJsonArray(("objects"))) {
                     JsonObject nodeObject = jsonElement.getAsJsonObject();
                     if (("chat_node").equalsIgnoreCase(nodeObject.get(("type")).getAsString())) {
                        for (JsonElement messageElement : nodeObject.getAsJsonObject(("data")).getAsJsonArray(("messages"))) {
                           JsonObject messageObject = messageElement.getAsJsonObject();
                           if (messageObject.get(("id")).getAsLong() > previousMessageId
                              && !messageObject.get(("author")).getAsString().equals(this.marketplaceUserId)
                              && !messageObject.get(("author")).getAsString().equals(("0"))) {
                              String messageText = this.textByHtmlClass(messageObject.get(("html")).getAsString(), ("chat-msg-text"));
                              String normalizedReply = messageText.replaceAll(("[^a-zA-Zа-яА-Я]"), "").toLowerCase();
                              AutoDeliver.PaidOrder buyerOrder = this.findBuyerOrder(conversation.buyerLogin);
                              if (buyerOrder == null
                                 || conversation.phase != AutoDeliver.ConversationPhase.NICKNAME
                                 || !normalizedReply.equals(("отмена")) && !normalizedReply.equals(("otmena"))) {
                                 if (conversation.phase == AutoDeliver.ConversationPhase.NICKNAME) {
                                    conversation.nickname = messageText.replaceAll(("[^a-zA-Z0-9_]"), "");
                                    conversation.phase = AutoDeliver.ConversationPhase.CONFIRM_NICKNAME;
                                    this.sendBuyerMessage(
                                       conversation.id,
                                       "\ud83d\udd0e Проверьте данные перед выдачей\n\n\ud83d\udc64 Ник: "
                                          + conversation.nickname
                                          + "\n\ud83d\udce6 Сумма: "
                                          + (buyerOrder != null ? buyerOrder.units() : 0)
                                          + " кк\n\n✅ Всё верно — напишите «Да» или «+»\n✏️ Ошибся в нике — напишите «Нет» или «-»"
                                    );
                                 } else if (conversation.phase == AutoDeliver.ConversationPhase.CONFIRM_NICKNAME) {
                                    if (!normalizedReply.equals(("да")) && !normalizedReply.equals(("da")) && !messageText.contains(("+")) && !messageText.contains(("✓"))) {
                                       if (normalizedReply.equals(("нет")) || normalizedReply.equals(("net")) || messageText.contains(("-")) || messageText.contains(("✗"))) {
                                          conversation.phase = AutoDeliver.ConversationPhase.NICKNAME;
                                          this.sendBuyerMessage(conversation.id, ("✏️ Хорошо, напишите ваш игровой ник ещё раз одним сообщением"));
                                       }
                                    } else if (buyerOrder != null && buyerOrder.units() > 0) {
                                       AutoDeliver.DeliveryRequest deliveryRequest = new AutoDeliver.DeliveryRequest();
                                       deliveryRequest.nickname = conversation.nickname;
                                       deliveryRequest.conversationId = conversation.id;
                                       deliveryRequest.orderId = buyerOrder.id();
                                       deliveryRequest.millionUnits = buyerOrder.units();
                                       this.deliveryQueue.add(deliveryRequest);
                                       this.sendBuyerMessage(
                                          conversation.id,
                                          "\ud83d\ude80 Данные приняты, начинаю выдачу!\n\n1️⃣ Зайдите на Анархию-"
                                             + this.configuredAnarchy()
                                             + "\n2️⃣ Примите приглашение в клан — оно уже летит вам\n3️⃣ Дождитесь моего сообщения и напишите /clan withdraw all\n\n\ud83d\udce6 К выдаче: "
                                             + buyerOrder.units()
                                             + " кк"
                                       );
                                       conversation.phase = AutoDeliver.ConversationPhase.DELIVERY;
                                       conversation.deliveryStartedAt = System.currentTimeMillis();
                                    } else {
                                       this.sendBuyerMessage(conversation.id, ("⏳ Не вижу ваш оплаченный заказ, напишите мне через минуту — выдам сразу же!"));
                                    }
                                 }
                              } else {
                                 conversation.phase = null;
                                 this.refundOrder(buyerOrder.id());
                                 this.sendBuyerMessage(conversation.id, ("↩️ Заказ отменён, средства вернулись на ваш баланс FunPay\n\n💛 Будем рады видеть вас снова!"));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private AutoDeliver() {
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((5L))).followRedirects(Redirect.NORMAL).build();
      this.sessionCookies = new HashMap<>();
      this.conversationTag = Integer.toHexString((int)(System.nanoTime() & (2147483647L)));
      this.seenOrderIds = new ArrayList<>();
      this.deliveryQueue = new ConcurrentLinkedQueue<>();
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null && System.currentTimeMillis() - this.lastGameActionAt >= (300L)) {
            AutoDeliver.DeliveryRequest deliveryRequest = this.deliveryQueue.peek();
            if (deliveryRequest != null) {
               this.idleHubSent = false;
               if (deliveryRequest.withdrawalObserved) {
                  if (minecraftClient.currentScreen != null
                     && minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler kickHandler
                     && minecraftClient.currentScreen.getTitle().getString().contains(("Кикнуть"))) {
                     net.minecraft.inventory.Inventory kickInventory = kickHandler.getInventory();

                     for (int kickSlot = 0; kickSlot < kickInventory.size(); kickSlot++) {
                        if (kickInventory.getStack(kickSlot).getName().getString().contains(("Подтвердить"))) {
                           InventoryActions.clickSlot(kickSlot, 0, SlotActionType.PICKUP, true);
                           minecraftClient.player.closeHandledScreen();
                           this.deliveryQueue.poll();
                           this.lastGameActionAt = System.currentTimeMillis();
                           if (this.pollExecutor != null) {
                              this.pollExecutor
                                 .execute(
                                    () -> this.sendBuyerMessage(
                                       deliveryRequest.conversationId,
                                       "✅ Готово, валюта у вас — приятной игры!\n\n\ud83d\ude4f Подтвердите заказ и оставьте отзыв, мне это очень поможет:\n\ud83d\udd17 https://funpay.com/orders/"
                                          + (deliveryRequest.orderId.startsWith(("#")) ? deliveryRequest.orderId.substring(1) : deliveryRequest.orderId)
                                          + "/\n\n\ud83d\udc9b Спасибо за покупку!"
                                    )
                                 );
                           }

                           return;
                        }
                     }
                  }

                  if (System.currentTimeMillis() - deliveryRequest.lastKickAt >= (1500L)) {
                     deliveryRequest.lastKickAt = System.currentTimeMillis();
                     this.lastGameActionAt = System.currentTimeMillis();
                     minecraftClient.player.networkHandler.sendChatCommand("clan kick " + deliveryRequest.nickname);
                  }
               } else if (this.currentAnarchy() != this.configuredAnarchy()) {
                  if (System.currentTimeMillis() - this.lastAnarchySwitchAt >= (5000L)) {
                     this.lastAnarchySwitchAt = System.currentTimeMillis();
                     this.lastGameActionAt = System.currentTimeMillis();
                     minecraftClient.player.networkHandler.sendChatCommand(this.deliveryAnarchy.getText().replace(("/"), ""));
                  }
               } else if (!deliveryRequest.joinedClan) {
                  if (minecraftClient.getNetworkHandler() != null && minecraftClient.getNetworkHandler().getPlayerListEntry(deliveryRequest.nickname) != null) {
                     if (System.currentTimeMillis() - deliveryRequest.lastInviteAt >= (2500L)) {
                        deliveryRequest.lastInviteAt = System.currentTimeMillis();
                        this.lastGameActionAt = System.currentTimeMillis();
                        minecraftClient.player.networkHandler.sendChatCommand("clan invite " + deliveryRequest.nickname);
                     }
                  }
               } else if (!deliveryRequest.investmentSent) {
                  deliveryRequest.investmentSent = true;
                  this.lastGameActionAt = System.currentTimeMillis();
                  minecraftClient.player.networkHandler.sendChatCommand("clan invest " + deliveryRequest.millionUnits * (1000000L));
               } else if (deliveryRequest.withdrawalPermissionGranted) {
                  if (!deliveryRequest.withdrawalInstructionsSent) {
                     deliveryRequest.withdrawalInstructionsSent = true;
                     if (this.pollExecutor != null) {
                        this.pollExecutor
                           .execute(
                              () -> this.sendBuyerMessage(
                                 deliveryRequest.conversationId,
                                 "\ud83d\udcb0 Права выданы, ваши "
                                    + deliveryRequest.millionUnits
                                    + " кк уже лежат в казне клана!\n\n\ud83d\udce5 Напишите в игровом чате: /clan withdraw all\n\ud83d\udcb5 Деньги упадут вам сразу же\n\n⚡ После вывода я выкину вас из клана — так и задумано"
                              )
                           );
                     }
                  }
               } else {
                  if (minecraftClient.currentScreen != null && minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler clanHandler) {
                     net.minecraft.inventory.Inventory clanInventory = clanHandler.getInventory();
                     int buyerSlot = -1;
                     int memberListSlot = -1;

                     for (int clanSlot = 0; clanSlot < clanInventory.size(); clanSlot++) {
                        String slotName = clanInventory.getStack(clanSlot).getName().getString();
                        if (buyerSlot == -1 && slotName.toLowerCase().contains(deliveryRequest.nickname.toLowerCase())) {
                           buyerSlot = clanSlot;
                        }

                        if (memberListSlot == -1 && slotName.contains(("Список участников"))) {
                           memberListSlot = clanSlot;
                        }
                     }

                     if (buyerSlot != -1) {
                        InventoryActions.clickSlot(buyerSlot, 0, SlotActionType.QUICK_MOVE, true);
                        minecraftClient.player.closeHandledScreen();
                        deliveryRequest.withdrawalPermissionGranted = true;
                        this.lastGameActionAt = System.currentTimeMillis();
                        return;
                     }

                     if (memberListSlot != -1) {
                        InventoryActions.clickSlot(memberListSlot, 0, SlotActionType.PICKUP, true);
                        this.lastGameActionAt = System.currentTimeMillis();
                        return;
                     }
                  }

                  if (System.currentTimeMillis() - deliveryRequest.lastMenuOpenAt >= (1500L)) {
                     deliveryRequest.lastMenuOpenAt = System.currentTimeMillis();
                     this.lastGameActionAt = System.currentTimeMillis();
                     minecraftClient.player.networkHandler.sendChatCommand(("clan menu"));
                  }
               }
            } else if (!this.idleHubSent) {
               this.idleHubSent = true;
               this.lastGameActionAt = System.currentTimeMillis();
               minecraftClient.player.networkHandler.sendChatCommand(("hub"));
            }
         }
      };
      this.packetListener = event -> {
         if (event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket chatPacket) {
            String messageText = chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase();

            for (AutoDeliver.DeliveryRequest deliveryRequest : this.deliveryQueue) {
               if (deliveryRequest.nickname != null && messageText.contains(deliveryRequest.nickname.toLowerCase())) {
                  if (messageText.contains(("присоединился к клану"))) {
                     deliveryRequest.joinedClan = true;
                  } else if (messageText.contains(("из казны клана"))) {
                     deliveryRequest.withdrawalObserved = true;
                  }
               }
            }
         }
      };
      this.goldenKey = this.addSetting(new FilteredTextSetting(("Golden-Key"), ("Ключ сессии FunPay из cookie golden_key."), "", 64));
      this.pricePerMillion = this.addSetting(new FilteredTextSetting(("Цена за 1кк"), ("Своя цена лота без комиссии FunPay, пусто - обслуживать любые заказы на валюту."), "", 16));
      this.sellAboveMillion = this.addSetting(new DecimalSetting(("Продавать при сумме от ляма"), ("Заказы меньше этой суммы бот возвращает покупателю."), (10.0D), 1.0, (50.0D), 1.0));
      this.deliveryAnarchy = this.addSetting(new FilteredTextSetting(("Анархия выдачи"), ("Куда бот летит выдавать и куда зовёт покупателя."), ("an305"), 16));
   }

   private Pattern htmlClassPattern(String className) {
      return Pattern.compile("class=\"[^\"]*(?<![-\\w])" + className + "(?![-\\w])[^\"]*\"");
   }

   private String textByHtmlClass(String html, String className) {
      Matcher classMatcher = this.htmlClassPattern(className).matcher(html);
      if (!classMatcher.find()) {
         return "";
      }

      int tagEnd = html.indexOf(62, classMatcher.end());

      while (tagEnd != -1) {
         int nextTagStart = html.indexOf(60, tagEnd + 1);
         String decodedText = this.decodeHtmlEntities(nextTagStart == -1 ? html.substring(tagEnd + 1) : html.substring(tagEnd + 1, nextTagStart)).trim();
         if (!decodedText.isEmpty()) {
            return decodedText;
         }

         if (nextTagStart == -1) {
            return "";
         }

         tagEnd = html.indexOf(62, nextTagStart);
      }

      return "";
   }

   private HttpResponse<String> sendRunnerRequest(String objectsJson, String requestJson) throws Exception {
      String formBody = "objects="
         + URLEncoder.encode(objectsJson == null ? "" : objectsJson, StandardCharsets.UTF_8)
         + "&request="
         + URLEncoder.encode(requestJson == null ? "" : requestJson, StandardCharsets.UTF_8)
         + "&csrf_token="
         + URLEncoder.encode(this.csrfToken == null ? "" : this.csrfToken, StandardCharsets.UTF_8);
      HttpResponse runnerResponse = this.httpClient
         .send(
            HttpRequest.newBuilder(URI.create(("https://funpay.com/runner/")))
               .timeout(Duration.ofSeconds((5L)))
               .header(("cookie"), this.cookieHeader())
               .header(("accept"), ("*/*"))
               .header(("content-type"), ("application/x-www-form-urlencoded; charset=UTF-8"))
               .header(("x-requested-with"), ("XMLHttpRequest"))
               .POST(BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
               .build(),
            BodyHandlers.ofString()
         );
      this.captureResponseCookies(runnerResponse);
      return runnerResponse;
   }

   private void refreshPaidOrders() {
      java.util.ArrayList<com.mouseclient.util.time.AutoDeliver.PaidOrder> latestOrders = this.fetchPaidOrders();
      if (latestOrders != null) {
         java.util.ArrayList<com.mouseclient.util.time.AutoDeliver.PaidOrder> previousOrders = this.paidOrders;
         this.paidOrders = latestOrders;
         if (previousOrders != null) {
            if (this.conversations != null) {
               for (AutoDeliver.PaidOrder previousOrder : previousOrders) {
                  if (!latestOrders.stream().anyMatch(paidOrder -> paidOrder.id().equals(previousOrder.id()))) {
                     this.conversations
                        .stream()
                        .filter(buyerConversation -> buyerConversation.buyerLogin.equalsIgnoreCase(previousOrder.buyerLogin()))
                        .filter(buyerConversation -> buyerConversation.phase != null)
                        .findFirst()
                        .ifPresent(buyerConversation -> buyerConversation.phase = null);
                  }
               }

               for (AutoDeliver.PaidOrder newOrder : latestOrders) {
                  if (!this.seenOrderIds.contains(newOrder.id())) {
                     this.seenOrderIds.add(newOrder.id());
                     this.conversations
                        .stream()
                        .filter(buyerConversation -> buyerConversation.buyerLogin.equalsIgnoreCase(newOrder.buyerLogin()))
                        .filter(buyerConversation -> buyerConversation.phase == null)
                        .findFirst()
                        .ifPresent(
                           buyerConversation -> {
                              if (newOrder.units() < this.sellAboveMillion.getValue()) {
                                 this.refundOrder(newOrder.id());
                                 this.sendBuyerMessage(
                                    buyerConversation.id,
                                    "❌ Минимальный заказ — "
                                       + this.sellAboveMillion.intValue()
                                       + " кк, ваш меньше\n\n\ud83d\udcb8 Средства уже вернулись на ваш баланс FunPay, извините за неудобство!"
                                 );
                              } else {
                                 this.sendBuyerMessage(
                                    buyerConversation.id,
                                    "\ud83d\udc4b Привет! На связи автовыдача валюты — маусклиент\n\n\ud83d\udce6 Ваш заказ: "
                                       + newOrder.units()
                                       + " кк\n✏️ Напишите ваш игровой ник одним сообщением\n\nℹ️ Передумали? Напишите «Отмена» — сразу вернём средства"
                                 );
                                 buyerConversation.phase = AutoDeliver.ConversationPhase.NICKNAME;
                              }
                           }
                        );
                  }
               }
            }
         } else {
            for (AutoDeliver.PaidOrder initialOrder : latestOrders) {
               this.seenOrderIds.add(initialOrder.id());
            }
         }
      }
   }

   public static AutoDeliver getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoDeliver();
      }

      return INSTANCE;
   }

   private AutoDeliver.PaidOrder findBuyerOrder(String buyerLogin) {
      return this.paidOrders == null ? null : this.paidOrders.stream().filter(paidOrder -> paidOrder.buyerLogin().equalsIgnoreCase(buyerLogin)).findFirst().orElse(null);
   }

   private static final class DeliveryRequest {
      private String nickname;
      private String conversationId;
      private String orderId;
      private int millionUnits;
      private long lastInviteAt;
      private long lastMenuOpenAt;
      private long lastKickAt;
      private boolean joinedClan;
      private boolean investmentSent;
      private boolean withdrawalPermissionGranted;
      private boolean withdrawalInstructionsSent;
      private boolean withdrawalObserved;
   }

   private record PaidOrder(String id, String buyerLogin, double price, int units) {
   }

   private static final class BuyerConversation {
      private String id;
      private String buyerLogin;
      private String nickname;
      private long latestMessageId;
      private AutoDeliver.ConversationPhase phase;
      private long deliveryStartedAt;
   }

   private enum ConversationPhase {
      NICKNAME,
      CONFIRM_NICKNAME,
      DELIVERY;
   }
}

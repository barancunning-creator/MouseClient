package com.mouseclient.network;

import com.mouseclient.events.impl.StagedEvent;

public class ClientTickEvent extends Event {
   public ClientTickEvent(StagedEvent.EventStage stage) {
      this.setStage(stage);
   }
}

package dev.freireservices.social_altruism;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;

public class ChatQuickStart {
  public static void main(String[] args) {
    // #actor-system
    ActorSystem.create(Main.create(), "ChatRoomDemo");
  }

  public static class Main {
    public static Behavior<Void> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<ChatRoom.RoomCommand> chatRoom = context.spawn(ChatRoom.create(), "chatRoom");
            ActorRef<ChatRoom.SessionEvent> gabbler = context.spawn(Gabbler.create(), "gabbler");
            context.watch(gabbler);
            chatRoom.tell(new ChatRoom.GetSession("ol’ Gabbler", gabbler));

            return Behaviors.receive(Void.class)
                .onSignal(Terminated.class, sig -> Behaviors.stopped())
                .build();
          });
    }
  }
  // #actor-system
}

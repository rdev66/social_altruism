package dev.freireservices.social_altruism.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ChatQuickStart {

  public static void main(String[] args) {
    // #actor-system
    ActorSystem.create(Main.create(), "PotRoom");
  }

  public static class Main {

    static List<ActorRef<ChatPotProtocol.SessionEvent>> sessions = new ArrayList<>();

    public static Behavior<Void> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<ChatPotProtocol.RoomCommand> chatRoom = context.spawn(ChatPotProtocol.create(100), "potRoom");

            // Agregamos jugadores
            sessions.addAll(
                IntStream.range(0, 100)
                    .mapToObj(
                        i ->
                            context.spawn(
                                Participante.create(getRandomNumberBetween(0, 100)), "participante-" + i))
                    .toList());

            sessions.forEach(s -> chatRoom.tell(new ChatPotProtocol.EnterPot(s, getRandomNumberBetween(0, 100))));



            return Behaviors.receive(Void.class)
                .onSignal(Terminated.class, sig -> Behaviors.stopped())
                .build();
          });
    }
  }

  // #actor-system

  public static int getRandomNumberBetween(int min, int max) {
    SecureRandom secureRandom = new SecureRandom();
    return secureRandom.nextInt(max - min) + min;
  }
}

package dev.freireservices.social_altruism.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.commands.Commands;
import dev.freireservices.social_altruism.chat.events.Events;
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

    static List<ActorRef<Events.SessionEvent>> sessions = new ArrayList<>();

    static final int numberOfParticipants = 2;
    static final int numberOfTurns = 100;

    public static Behavior<Void> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<Commands.RoomCommand> chatRoom =
                context.spawn(
                    ChatPotProtocol.create(numberOfParticipants, numberOfTurns), "potRoom");

            // Agregamos jugadores
            sessions.addAll(
                IntStream.range(0, numberOfParticipants)
                    .mapToObj(
                        i ->
                            context.spawn(
                                Participante.create(getRandomNumberBetween(0, 100)),
                                "participante-" + i))
                    .toList());

            // Entrar en pot
            for (ActorRef<Events.SessionEvent> session : sessions) {
              chatRoom.tell(new Commands.EnterPot(session));
            }

            for (int i = 0; i < numberOfTurns; i++) {
              // Participar en cada turno cantidad diferente
              for (ActorRef<Events.SessionEvent> session : sessions) {

                // Pícaro
                if (session.path().name().contains("participante-0")) {
                  chatRoom.tell(new Commands.PlayTurn(session, 0));
                } else {
                  chatRoom.tell(new Commands.PlayTurn(session, getRandomNumberBetween(0, 10)));
                }
              }
            }

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

package dev.freireservices.social_altruism.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;

import java.util.ArrayList;
import java.util.List;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;

public class PotQuickStart {

  public static final int MONEDAS_INIT = 100;

  public static void main(String[] args) {
    // #actor-system
    ActorSystem.create(Main.create(), "PotRoom");
  }

  public static class Main {

    static List<ActorRef<ParticipantProtocol.ParticipantMessage>> sessions = new ArrayList<>();

    static final int numberOfParticipants = 3;
    static final int numberOfTurns = 100;

    public static Behavior<Void> create() {
      return Behaviors.setup(
          context -> {
            ActorRef<PotRoomProtocol.PotRoomMessage> chatRoom =
                context.spawn(
                    PotRoom.create(numberOfParticipants, numberOfTurns), "potRoom");

            // Agregamos jugadores
            final var picaroZero = Participant.create(MONEDAS_INIT, PICARO);
            final var santoZero = Participant.create(MONEDAS_INIT, SANTO);
            final var justiZero = Participant.create(MONEDAS_INIT, JUSTICIERO);

            sessions.add(context.spawn(picaroZero, "participante-picaro-0"));
            sessions.add(context.spawn(santoZero, "participante-santo-0"));
            sessions.add(context.spawn(justiZero, "participante-justiciero-0"));

            // Entrar en pot
            for (ActorRef<ParticipantProtocol.ParticipantMessage> session : sessions) {
              chatRoom.tell(new PotRoomProtocol.EnterPot(session));
            }

/*            for (int i = 0; i < numberOfTurns; i++) {
              // Participar en cada turno cantidad diferente
              for (ActorRef<ParticipantProtocol.ParticipantMessage> session : participants) {

                // Pícaro
                if (session.path().name().contains("participante-0")) {
                  chatRoom.tell(new PotRoomProtocol.PlayTurn(session, 0));
                  // Justiciero
                } else if (session.path().name().contains("participante-1")) {

                  //      ActorRef<Commands.RoomCommand> chatRoom, double pot,
                  // ActorRef<Events.SessionEvent> replyTo)

                  chatRoom.tell(new PotRoomProtocol.PlayTurn(session, 1));
                  // Santo
                } else if (session.path().name().contains("participante-2")) {
                  chatRoom.tell(new PotRoomProtocol.PlayTurn(session, 2));
                }
              }
            }*/

            return Behaviors.receive(Void.class)
                .onSignal(Terminated.class, sig -> Behaviors.stopped())
                .build();
          });
    }
  }

  // #actor-system
}

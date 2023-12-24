package dev.freireservices.social_altruism.chat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.commands.Commands;
import dev.freireservices.social_altruism.chat.events.Events;
import java.time.Duration;
import org.junit.Test;

// #definition
public class ChatQuickStartTest {

  // #definition

  // #test
  @Test
  // FIXME - Improve or delete..
  public void testCooperationCaseOne() {

    final ActorTestKit testKit = ActorTestKit.create();

    TestProbe<Events.SessionEvent> testProbe = testKit.createTestProbe();

    ActorRef<Commands.RoomCommand> chatRoomTest =
        testKit.spawn(ChatPotProtocol.create(2, 1), "chatRoom");

    ActorRef<Events.SessionEvent> participanteUno =
        testKit.spawn(Participante.create(100), "participanteUno");

    ActorRef<Events.SessionEvent> participanteDos =
        testKit.spawn(Participante.create(10), "participanteDos");

    ActorRef<Events.SessionEvent> participanteTres =
        testKit.spawn(Participante.create(100), "participanteTres");

    ActorRef<Events.SessionEvent> participanteCuatro =
        testKit.spawn(Participante.create(10), "participanteCuatro");

    // Enter POT

    chatRoomTest.tell(new Commands.EnterPot(participanteUno));
    chatRoomTest.tell(new Commands.EnterPot(participanteDos));

    chatRoomTest.tell(new Commands.EnterPot(participanteTres));
    chatRoomTest.tell(new Commands.EnterPot(participanteCuatro));

    // Turnos
    chatRoomTest.tell(new Commands.PlayTurn(participanteUno, 1));
    chatRoomTest.tell(new Commands.PlayTurn(participanteDos, 2));
    chatRoomTest.tell(new Commands.PlayTurn(participanteTres, 3));
    chatRoomTest.tell(new Commands.PlayTurn(participanteCuatro, 4));

    // #assert
    // #assert

  }

  @Test
  public void testActorGetsUserDenied() {
    final ActorTestKit testKit = ActorTestKit.create();
    TestProbe<Events.SessionEvent> testProbe = testKit.createTestProbe();

    ActorRef<Commands.RoomCommand> chatRoomTest =
        testKit.spawn(ChatPotProtocol.create(2, 1), "chatRoom");

    chatRoomTest.tell(new Commands.EnterPot(testProbe.ref()));

    testProbe.expectMessageClass(Events.SessionGranted.class, Duration.ofSeconds(10));

    chatRoomTest.tell(new Commands.EnterPot(testProbe.ref()));

    Events.SessionDenied sessionDenied =
        new Events.SessionDenied("Can only enter a pot once");

    testProbe.expectMessage(Duration.ofSeconds(10), sessionDenied);

    // #assert
  }
}

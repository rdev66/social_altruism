package dev.freireservices.social_altruism.chat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import java.time.Duration;
import org.junit.Test;

// #definition
public class ChatQuickStartTest {

  // #definition

  // #test
  @Test
  public void testCooperationCaseOne() {

    final ActorTestKit testKit = ActorTestKit.create();

    TestProbe<ChatPotProtocol.SessionEvent> testProbe = testKit.createTestProbe();

    ActorRef<ChatPotProtocol.RoomCommand> chatRoomTest =
        testKit.spawn(ChatPotProtocol.create(2), "chatRoom");

    ActorRef<ChatPotProtocol.SessionEvent> participanteUno =
        testKit.spawn(Participante.create(100), "participanteUno");

    ActorRef<ChatPotProtocol.SessionEvent> participanteDos =
        testKit.spawn(Participante.create(10), "participanteDos");

    ActorRef<ChatPotProtocol.SessionEvent> participanteTres =
            testKit.spawn(Participante.create(100), "participanteTres");

    ActorRef<ChatPotProtocol.SessionEvent> participanteCuatro =
            testKit.spawn(Participante.create(10), "participanteCuatro");


    // Turnos

    chatRoomTest.tell(new ChatPotProtocol.EnterPot(participanteUno, 10));
    chatRoomTest.tell(new ChatPotProtocol.EnterPot(participanteDos, 1));

    chatRoomTest.tell(new ChatPotProtocol.EnterPot(participanteTres, 11));
    chatRoomTest.tell(new ChatPotProtocol.EnterPot(participanteCuatro, 2));

    // #assert
    // #assert

  }

  @Test
  public void testActorGetsUserDenied() {
    final ActorTestKit testKit = ActorTestKit.create();
    TestProbe<ChatPotProtocol.SessionEvent> testProbe = testKit.createTestProbe();

    ActorRef<ChatPotProtocol.RoomCommand> chatRoomTest =
        testKit.spawn(ChatPotProtocol.create(2), "chatRoom");

    chatRoomTest.tell(new ChatPotProtocol.EnterPot(testProbe.ref(), 10));

    testProbe.expectMessageClass(ChatPotProtocol.SessionGranted.class, Duration.ofSeconds(10));

    chatRoomTest.tell(new ChatPotProtocol.EnterPot(testProbe.ref(), 10));

    ChatPotProtocol.SessionDenied sessionDenied =
        new ChatPotProtocol.SessionDenied("Can only enter a pot once");

    testProbe.expectMessage(Duration.ofSeconds(10), sessionDenied);

    // #assert
  }
}

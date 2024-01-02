package dev.freireservices.social_altruism.chat;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.events.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import java.time.Duration;
import java.util.List;
import org.junit.Test;

// #definition
public class PotQuickStartTest {

  // #definition

  // #test
  @Test
  // FIXME - Improve or delete..
  public void testCooperationCaseOne() {

    final ActorTestKit testKit = ActorTestKit.create();

    ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
        testKit.spawn(PotRoom.create(3, 1), "potRoom");

    ActorRef<ParticipantProtocol.ParticipantMessage> p1 = testKit.spawn(Participant.create(100, PICARO), "PICARO-1");

    ActorRef<ParticipantProtocol.ParticipantMessage> p2 = testKit.spawn(Participant.create(100, JUSTICIERO), "JUSTICIERO-1");

    ActorRef<ParticipantProtocol.ParticipantMessage> p3 = testKit.spawn(Participant.create(10, SANTO), "SANTO-1");

    final List<ActorRef<ParticipantProtocol.ParticipantMessage>> sessions = List.of(p1, p2, p3);

    // Enter POT
    chatRoomTest.tell(new PotRoomProtocol.EnterPot(p1));
    chatRoomTest.tell(new PotRoomProtocol.EnterPot(p2));
    chatRoomTest.tell(new PotRoomProtocol.EnterPot(p3));

    // Turnos
    chatRoomTest.tell(new PotRoomProtocol.PlayTurn(p1,0));
    chatRoomTest.tell(new PotRoomProtocol.PlayTurn(p2,1));
    chatRoomTest.tell(new PotRoomProtocol.PlayTurn(p3, 3));

    // #assert
    // #assert

  }

  @Test
  public void testActorGetsUserDenied() {
    final ActorTestKit testKit = ActorTestKit.create();
    TestProbe<ParticipantProtocol.ParticipantMessage> testProbe = testKit.createTestProbe("TestProbe");

    ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
        testKit.spawn(PotRoom.create(2, 1), "chatRoom");

    chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

    testProbe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(10));

    chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

    testProbe.expectMessage(
        Duration.ofSeconds(10), new ParticipantProtocol.SessionDenied("Can only enter a pot once"));

    // #assert
  }
}

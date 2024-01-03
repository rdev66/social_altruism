package dev.freireservices.social_altruism.chat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;
import org.junit.Test;

import java.time.Duration;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;

public class ProtocolTests {
    public static final int INITIAL_COINS = 100;
    public static final int TOTAL_PARTICIPANTS = 4;

    @Test
    public void testSessionStartedOnJoinParticipants() {

        final ActorTestKit testKit = ActorTestKit.create();

        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe", ParticipantMessage.class);

        var potRoom = PotRoom.create(TOTAL_PARTICIPANTS, 1);
        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(potRoom, "potRoom");

        BehaviorTestKit<PotRoomProtocol.PotRoomMessage> test = BehaviorTestKit.create(potRoom);

        ActorRef<ParticipantMessage> p1 =
                testKit.spawn(Participant.create(INITIAL_COINS, PICARO), "PICARO-1");

        ActorRef<ParticipantMessage> p2 =
                testKit.spawn(Participant.create(INITIAL_COINS, JUSTICIERO), "JUSTICIERO-1");

        ActorRef<ParticipantMessage> p3 =
                testKit.spawn(Participant.create(INITIAL_COINS, SANTO), "SANTO-1");

        // Enter POT
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(p1));
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(p2));
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(p3));
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

        //Session started

        testProbe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(20));
        testProbe.expectMessageClass(ParticipantProtocol.SessionStarted.class, Duration.ofSeconds(20));

    }


    @Test
    public void testActorGetsSessionGranted() {
        final ActorTestKit testKit = ActorTestKit.create();
        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe");

        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(PotRoom.create(1, 1), "chatRoom");

        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

        testProbe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(10));

        // #assert
    }

    @Test
    public void testActorGetsSessionDenied() {
        final ActorTestKit testKit = ActorTestKit.create();
        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe");

        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(PotRoom.create(2, 1), "chatRoom");

        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

        testProbe.expectMessageClass(ParticipantProtocol.SessionDenied.class, Duration.ofSeconds(5));

        // #assert
    }

    @Test
    public void testMultipleSessions() {
        final ActorTestKit testKit = ActorTestKit.create();
        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe");

        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(PotRoom.create(2, 1), "chatRoom");

        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));
        chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

        testProbe.expectMessageClass(ParticipantProtocol.SessionDenied.class, Duration.ofSeconds(5));

        // #assert
    }
}

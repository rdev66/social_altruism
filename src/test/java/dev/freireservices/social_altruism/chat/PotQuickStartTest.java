package dev.freireservices.social_altruism.chat;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.SessionEnded;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.SessionStarted;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.Session;

import java.time.Duration;
import java.util.List;

import dev.freireservices.social_altruism.chat.potroom.SessionProtocol;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;
import org.junit.Test;

public class PotQuickStartTest {
    public static final int INITIAL_COINS = 100;
    public static final int TOTAL_PARTICIPANTS = 3;

    @Test
    // FIXME - Improve or delete..
    public void testCooperationCaseOne() throws InterruptedException {

        final ActorTestKit testKit = ActorTestKit.create();

        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe");

        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(PotRoom.create(3, 1), "potRoom");

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
        //chatRoomTest.tell(new PotRoomProtocol.EnterPot(testProbe.ref()));

        //Session started

        testProbe.expectMessageClass(SessionEnded.class, Duration.ofSeconds(20));


    }

    @Test
    public void testActorGetsUserDenied() {
        final ActorTestKit testKit = ActorTestKit.create();
        TestProbe<ParticipantMessage> testProbe =
                testKit.createTestProbe("TestProbe");

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

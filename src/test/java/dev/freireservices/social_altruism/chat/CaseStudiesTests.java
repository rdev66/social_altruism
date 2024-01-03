package dev.freireservices.social_altruism.chat;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;

public class CaseStudiesTests {
    public static final int INITIAL_COINS = 100;
    public static final int TOTAL_PARTICIPANTS = 3;
    public static final int TOTAL_TURNS = 100;

    final static ActorTestKit testKit = ActorTestKit.create();

    @Test
    public void testCooperation() {

        var potRoom = PotRoom.create(TOTAL_PARTICIPANTS, TOTAL_TURNS);

        ActorRef<PotRoomProtocol.PotRoomMessage> chatRoomTest =
                testKit.spawn(potRoom, "potRoom");

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

        try {
            TimeUnit.MINUTES.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @AfterClass
    public static void cleanup() {
        testKit.shutdownTestKit();
    }
}

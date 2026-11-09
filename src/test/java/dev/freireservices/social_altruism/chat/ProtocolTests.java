package dev.freireservices.social_altruism.chat;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.PotReturned;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.RequestFinalBalance;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.potroom.Session;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol;
import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ProtocolTests {

    private ActorTestKit testKit;

    @After
    public void cleanup() {
        if (testKit != null) {
            testKit.shutdownTestKit();
            testKit = null;
        }
    }

    @Test
    public void sessionGrantedAndStartedWhenPotFills() {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(4, 1);

        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        TestProbe<ParticipantMessage> probe =
                testKit.createTestProbe("probe", ParticipantMessage.class);

        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "potRoom");

        ActorRef<ParticipantMessage> p1 =
                testKit.spawn(Participant.create(config, PICARO), "PICARO-1");
        ActorRef<ParticipantMessage> p2 =
                testKit.spawn(Participant.create(config, JUSTICIERO), "JUSTICIERO-1");
        ActorRef<ParticipantMessage> p3 =
                testKit.spawn(Participant.create(config, SANTO), "SANTO-1");

        potRoom.tell(new PotRoomProtocol.EnterPot(p1, PICARO));
        potRoom.tell(new PotRoomProtocol.EnterPot(p2, JUSTICIERO));
        potRoom.tell(new PotRoomProtocol.EnterPot(p3, SANTO));
        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), SANTO));

        probe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(5));
        ParticipantProtocol.SessionStarted started =
                probe.expectMessageClass(
                        ParticipantProtocol.SessionStarted.class, Duration.ofSeconds(5));
        assertThat(started.participants(), hasSize(4));
        assertEquals(config.turnos(), started.config().turnos());
    }

    @Test
    public void actorGetsSessionGranted() {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(1, 1);
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        TestProbe<ParticipantMessage> probe = testKit.createTestProbe("probe");
        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "chatRoom");

        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), SANTO));

        probe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(5));
        probe.expectMessageClass(ParticipantProtocol.SessionStarted.class, Duration.ofSeconds(5));
    }

    @Test
    public void duplicateEnterDenied() {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(2, 1);
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        TestProbe<ParticipantMessage> probe = testKit.createTestProbe("probe");
        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "chatRoom");

        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), SANTO));
        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), SANTO));

        ParticipantProtocol.SessionDenied denied =
                probe.expectMessageClass(
                        ParticipantProtocol.SessionDenied.class, Duration.ofSeconds(5));
        assertEquals("Can only enter a pot once", denied.reason());
    }

    @Test
    public void potFullDeniedAfterStart() throws Exception {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(2, 1);
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "chatRoom");

        ActorRef<ParticipantMessage> p1 =
                testKit.spawn(Participant.create(config, SANTO), "SANTO-1");
        ActorRef<ParticipantMessage> p2 =
                testKit.spawn(Participant.create(config, SANTO), "SANTO-2");
        TestProbe<ParticipantMessage> late = testKit.createTestProbe("late");

        potRoom.tell(new PotRoomProtocol.EnterPot(p1, SANTO));
        potRoom.tell(new PotRoomProtocol.EnterPot(p2, SANTO));

        GameResult result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.balanceOk(), is(true));

        potRoom.tell(new PotRoomProtocol.EnterPot(late.ref(), SANTO));
        ParticipantProtocol.SessionDenied denied =
                late.expectMessageClass(
                        ParticipantProtocol.SessionDenied.class, Duration.ofSeconds(5));
        assertEquals("Pot full", denied.reason());
    }

    /**
     * TestProbe acts as a participant and verifies PotReturned.contributions has one entry per
     * player with their aporte.
     */
    @Test
    public void potReturnedIncludesContributionMap() {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(2, 1);
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        TestProbe<ParticipantMessage> probe =
                testKit.createTestProbe("probe-player", ParticipantMessage.class);

        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "potRoom");

        ActorRef<ParticipantMessage> santo =
                testKit.spawn(Participant.create(config, SANTO), "SANTO-0");

        potRoom.tell(new PotRoomProtocol.EnterPot(santo, SANTO));
        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), PICARO));

        probe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(5));
        ParticipantProtocol.SessionStarted started =
                probe.expectMessageClass(
                        ParticipantProtocol.SessionStarted.class, Duration.ofSeconds(5));

        started
                .replyTo()
                .tell(
                        new SessionProtocol.PlayTurn(
                                started.replyTo(),
                                probe.ref(),
                                started.participants(),
                                0,
                                0.0));

        PotReturned potReturned =
                probe.expectMessageClass(PotReturned.class, Duration.ofSeconds(5));
        assertThat(potReturned.contributions().size(), is(2));
        assertThat(potReturned.contributions(), hasKey(santo));
        assertThat(potReturned.contributions(), hasKey(probe.ref()));
        assertThat(potReturned.contributions().get(santo), closeTo(1.0, 1e-6));
        assertThat(potReturned.contributions().get(probe.ref()), closeTo(0.0, 1e-6));

        RequestFinalBalance request =
                probe.expectMessageClass(RequestFinalBalance.class, Duration.ofSeconds(5));
        // Pícaro: 100 − 0 + share(1) = 101
        request.session().tell(new SessionProtocol.ReportBalance(probe.ref(), 101.0));

        probe.expectMessageClass(ParticipantProtocol.SessionEnded.class, Duration.ofSeconds(5));
    }

    /** PlayTurn with wrong turn is ignored — ledger balance does not change. */
    @Test
    public void playTurnWrongTurnIgnored() throws Exception {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(1, 1, 100.0, 2.0, 1.0, Duration.ZERO);
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> sink = spawnSink(future);

        TestProbe<ParticipantMessage> probe = testKit.createTestProbe("solo");
        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, sink), "potRoom");
        potRoom.tell(new PotRoomProtocol.EnterPot(probe.ref(), SANTO));

        probe.expectMessageClass(ParticipantProtocol.SessionGranted.class, Duration.ofSeconds(5));
        ParticipantProtocol.SessionStarted started =
                probe.expectMessageClass(
                        ParticipantProtocol.SessionStarted.class, Duration.ofSeconds(5));

        ActorRef<SessionProtocol.SessionMessage> session = started.replyTo();

        session.tell(
                new SessionProtocol.PlayTurn(
                        session, probe.ref(), started.participants(), 99, 50.0));

        session.tell(
                new SessionProtocol.PlayTurn(
                        session, probe.ref(), started.participants(), 0, 1.0));

        PotReturned potReturned =
                probe.expectMessageClass(PotReturned.class, Duration.ofSeconds(5));
        assertThat(potReturned.returnedAmount(), closeTo(2.0, 1e-6));
        assertThat(potReturned.contributions().get(probe.ref()), closeTo(1.0, 1e-6));

        RequestFinalBalance request =
                probe.expectMessageClass(RequestFinalBalance.class, Duration.ofSeconds(5));
        request.session().tell(new SessionProtocol.ReportBalance(probe.ref(), 101.0));

        GameResult result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.balanceOk(), is(true));
        assertThat(result.players().get(0).finalCoins(), closeTo(101.0, 1e-6));
    }

    /** Tipo sin registrar → IllegalStateException. */
    @Test
    public void tipoSinRegistrarLanzaExcepcion() {
        testKit = ActorTestKit.create();
        GameConfig config = new GameConfig(1, 1);
        TestProbe<ParticipantMessage> probe = testKit.createTestProbe("orphan");
        TestProbe<GameResult> sink = testKit.createTestProbe("sink");

        Map<ActorRef<ParticipantMessage>, ParticipantType> emptyTypes = Map.of();
        List<ActorRef<ParticipantMessage>> participants = List.of(probe.ref());

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new Session(
                                        null, config, participants, emptyTypes, sink.ref()));
        assertThat(ex.getMessage(), containsString("Missing ParticipantType"));
    }

    private ActorRef<GameResult> spawnSink(CompletableFuture<GameResult> future) {
        return testKit.spawn(
                Behaviors.<GameResult>receive(
                        (ctx, msg) -> {
                            future.complete(msg);
                            return Behaviors.same();
                        }),
                "result-sink-" + System.nanoTime());
    }
}

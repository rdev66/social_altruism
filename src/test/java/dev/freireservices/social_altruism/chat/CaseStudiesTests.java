package dev.freireservices.social_altruism.chat;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;
import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

/**
 * Case studies a–g for the public-goods rules.
 */
public class CaseStudiesTests {

    private ActorTestKit testKit;

    @After
    public void cleanup() {
        if (testKit != null) {
            testKit.shutdownTestKit();
            testKit = null;
        }
    }

    /** a) All santos: each gains aporte×(mult-1)×turnos net (+1/turn with defaults). */
    @Test
    public void a_todosSantosGanan() {
        GameResult result = runMix(3, 20, 0, 3, 0);
        assertThat(result.balanceOk(), is(true));
        assertThat(result.averageFor(SANTO), closeTo(120.0, 1e-6));
        assertThat(result.totalWealth(), closeTo(360.0, 1e-6));
    }

    /** b) All pícaros: pot empty every turn; balances unchanged. */
    @Test
    public void b_todosPicarosSinCambio() {
        GameResult result = runMix(3, 20, 3, 0, 0);
        assertThat(result.balanceOk(), is(true));
        assertThat(result.averageFor(PICARO), closeTo(100.0, 1e-6));
        assertThat(result.totalWealth(), closeTo(300.0, 1e-6));
    }

    /** c) All justicieros: turn0 cooperation sustains forever → same as all santos. */
    @Test
    public void c_todosJusticierosCooperan() {
        GameResult result = runMix(3, 20, 0, 0, 3);
        assertThat(result.balanceOk(), is(true));
        assertThat(result.averageFor(JUSTICIERO), closeTo(120.0, 1e-6));
    }

    /** d) One pícaro among santos free-rides and finishes richer than contributors. */
    @Test
    public void d_unPicaroEntreSantosGanaMas() {
        GameResult result = runMix(3, 20, 1, 2, 0);
        assertThat(result.balanceOk(), is(true));
        assertThat(result.averageFor(PICARO), greaterThan(result.averageFor(SANTO)));
        // pot=2 each turn → share=4/3; picaro +4/3/turn; santo -1+4/3=+1/3/turn
        assertThat(result.averageFor(PICARO), closeTo(100.0 + 20 * (4.0 / 3.0), 1e-6));
        assertThat(result.averageFor(SANTO), closeTo(100.0 + 20 * (1.0 / 3.0), 1e-6));
    }

    /** e) Justiciero stops after turn 0 when a pícaro did not contribute. */
    @Test
    public void e_justicieroParaTrasPicaro() {
        GameResult result = runMix(2, 10, 1, 0, 1);
        assertThat(result.balanceOk(), is(true));
        // Turn 0 only: pot=1, share=1; justiciero 100-1+1=100; picaro 100+1=101
        assertThat(result.averageFor(JUSTICIERO), closeTo(100.0, 1e-6));
        assertThat(result.averageFor(PICARO), closeTo(101.0, 1e-6));
        assertThat(result.totalWealth(), closeTo(201.0, 1e-6));
    }

    /** f) Zero-coin players stay until SessionEnded (do not leave early). */
    @Test
    public void f_monedasCeroPermanecen() {
        // aporte=50, 2 turns, 2 santos → after turn0 each has 50; turn1 each contributes 50 → 0 then share
        GameConfig config = new GameConfig(2, 2, 50.0, 2.0, 50.0, Duration.ZERO);
        GameResult result = runWithConfig(config, 0, 2, 0);
        assertThat(result.balanceOk(), is(true));
        // Turn0: pot=100, share=100 → each 50-50+100=100
        // Turn1: pot=100, share=100 → each 100-50+100=150? Wait coins=100, aporte min(50,100)=50
        // Actually after turn0: 100 each. Turn1: contribute 50 → 50, pot=100, share=100 → 150
        assertThat(result.averageFor(SANTO), closeTo(150.0, 1e-6));
        assertTrue(result.players().stream().allMatch(p -> p.finalCoins() >= 0));
    }

    /** g) Balance check / wealth accounting matches pot×(mult-1) injection. */
    @Test
    public void g_balanceYMultiplicador() {
        GameResult mixed = runMix(4, 10, 1, 2, 1);
        assertThat(mixed.balanceOk(), is(true));
        // Expected wealth = 400 + sum over turns of pot_t
        // turn0: picaro0 + 2 santo + justiciero = pot 3; then justiciero stops
        // later turns: only 2 santos → pot 2 each
        double expected = 400.0 + 3.0 + 9 * 2.0;
        assertThat(mixed.totalWealth(), closeTo(expected, 1e-6));
    }

    private GameResult runMix(int players, int turns, int picaros, int santos, int justicieros) {
        GameConfig config = new GameConfig(players, turns, 100.0, 2.0, 1.0, Duration.ZERO);
        return runWithConfig(config, picaros, santos, justicieros);
    }

    private GameResult runWithConfig(
            GameConfig config, int picaros, int santos, int justicieros) {
        testKit = ActorTestKit.create();
        CompletableFuture<GameResult> future = new CompletableFuture<>();
        ActorRef<GameResult> resultRef =
                testKit.spawn(
                        org.apache.pekko.actor.typed.javadsl.Behaviors.<GameResult>receive(
                                (ctx, msg) -> {
                                    future.complete(msg);
                                    return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
                                }),
                        "result-sink");

        ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                testKit.spawn(PotRoom.create(config, resultRef), "potRoom");

        List<ActorRef<ParticipantMessage>> spawned = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < picaros; i++) {
            spawned.add(spawn(config, PICARO, "PICARO-" + idx++));
        }
        for (int i = 0; i < santos; i++) {
            spawned.add(spawn(config, SANTO, "SANTO-" + idx++));
        }
        for (int i = 0; i < justicieros; i++) {
            spawned.add(spawn(config, JUSTICIERO, "JUSTICIERO-" + idx++));
        }

        for (ActorRef<ParticipantMessage> p : spawned) {
            potRoom.tell(new PotRoomProtocol.EnterPot(p));
        }

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Game did not finish in time", e);
        }
    }

    private ActorRef<ParticipantMessage> spawn(
            GameConfig config, ParticipantType type, String name) {
        return testKit.spawn(Participant.create(config, type), name);
    }
}

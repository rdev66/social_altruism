package dev.freireservices.social_altruism.chat;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.Participant;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;
import dev.freireservices.social_altruism.chat.potroom.PotRoom;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Spawns a pot room and the requested participant mix, then waits for {@link GameResult}.
 */
public final class GameRunner {

    private GameRunner() {}

    public record Composition(int picaros, int santos, int justicieros) {
        public int total() {
            return picaros + santos + justicieros;
        }
    }

    public static GameResult run(
            GameConfig config, Composition composition, Duration timeout) {
        if (composition.total() != config.jugadores()) {
            throw new IllegalArgumentException(
                    "Composition size "
                            + composition.total()
                            + " != config.jugadores "
                            + config.jugadores());
        }

        ActorSystem<Guardian.Command> system =
                ActorSystem.create(Guardian.create(config, composition), "game-" + System.nanoTime());

        try {
            CompletionStage<GameResult> future =
                    AskPattern.ask(
                            system,
                            Guardian.Start::new,
                            timeout,
                            system.scheduler());
            return future.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Game run failed", e);
        } finally {
            system.terminate();
        }
    }

    private static final class Guardian {
        interface Command {}

        record Start(ActorRef<GameResult> replyTo) implements Command {}

        static Behavior<Command> create(GameConfig config, Composition composition) {
            return Behaviors.setup(
                    context ->
                            Behaviors.receive(Command.class)
                                    .onMessage(
                                            Start.class,
                                            start -> {
                                                ActorRef<PotRoomProtocol.PotRoomMessage> potRoom =
                                                        context.spawn(
                                                                PotRoom.create(
                                                                        config, start.replyTo()),
                                                                "potRoom");

                                                List<ActorRef<ParticipantMessage>> players =
                                                        new ArrayList<>();
                                                int idx = 0;
                                                for (int i = 0; i < composition.picaros(); i++) {
                                                    players.add(
                                                            context.spawn(
                                                                    Participant.create(
                                                                            config,
                                                                            ParticipantType.PICARO),
                                                                    "PICARO-" + (idx++)));
                                                }
                                                for (int i = 0; i < composition.santos(); i++) {
                                                    players.add(
                                                            context.spawn(
                                                                    Participant.create(
                                                                            config,
                                                                            ParticipantType.SANTO),
                                                                    "SANTO-" + (idx++)));
                                                }
                                                for (int i = 0; i < composition.justicieros(); i++) {
                                                    players.add(
                                                            context.spawn(
                                                                    Participant.create(
                                                                            config,
                                                                            ParticipantType
                                                                                    .JUSTICIERO),
                                                                    "JUSTICIERO-" + (idx++)));
                                                }

                                                for (ActorRef<ParticipantMessage> p : players) {
                                                    potRoom.tell(
                                                            new PotRoomProtocol.EnterPot(p));
                                                }
                                                return Behaviors.same();
                                            })
                                    .build());
        }
    }
}

package dev.freireservices.social_altruism.chat.potroom;

import dev.freireservices.social_altruism.chat.GameConfig;
import dev.freireservices.social_altruism.chat.GameResult;
import dev.freireservices.social_altruism.chat.GameResult.PlayerOutcome;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.PotReturned;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.SessionEnded;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Session {

    private final ActorContext<SessionMessage> context;
    private final GameConfig config;
    private final List<ActorRef<ParticipantMessage>> participants;
    private final ActorRef<GameResult> resultRef;

    /** Ledger of current balances (authoritative for GameResult / balance check). */
    private final Map<ActorRef<ParticipantMessage>, Double> balances = new LinkedHashMap<>();
    private final Map<ActorRef<ParticipantMessage>, ParticipantType> types = new HashMap<>();

    private int currentTurn = 0;
    private double currentPot = 0.0;
    private final Map<ActorRef<ParticipantMessage>, Double> turnContributions = new LinkedHashMap<>();

    /** Expected total wealth after each turn's multiplier injection. */
    private double expectedTotalWealth;
    private boolean ending;

    public Session(
            ActorContext<SessionMessage> context,
            GameConfig config,
            List<ActorRef<ParticipantMessage>> participants,
            ActorRef<GameResult> resultRef) {
        this.context = context;
        this.config = config;
        this.participants = List.copyOf(participants);
        this.resultRef = resultRef;
        for (ActorRef<ParticipantMessage> p : participants) {
            balances.put(p, config.monedasIniciales());
            types.put(p, inferType(p.path().name()));
        }
        this.expectedTotalWealth = config.monedasIniciales() * participants.size();
    }

    private static ParticipantType inferType(String name) {
        String upper = name.toUpperCase();
        if (upper.contains("PICARO")) {
            return ParticipantType.PICARO;
        }
        if (upper.contains("SANTO")) {
            return ParticipantType.SANTO;
        }
        if (upper.contains("JUSTICIERO") || upper.contains("JUSTI")) {
            return ParticipantType.JUSTICIERO;
        }
        return ParticipantType.SANTO;
    }

    /** Allow tests/sim to register exact types when names are ambiguous. */
    public void registerType(ActorRef<ParticipantMessage> ref, ParticipantType type) {
        types.put(ref, type);
    }

    public Behavior<SessionMessage> createSessionBehaviour() {
        return Behaviors.receive(SessionMessage.class)
                .onMessage(PlayTurn.class, this::onPlayTurn)
                .onMessage(ShareReturnPotWithParticipants.class, this::onShareReturn)
                .onMessage(EndSession.class, ignored -> onSessionEnded())
                .build();
    }

    public static Behavior<SessionMessage> create(
            GameConfig config,
            List<ActorRef<ParticipantMessage>> participants,
            ActorRef<GameResult> resultRef) {
        return Behaviors.setup(
                context ->
                        new Session(context, config, participants, resultRef)
                                .createSessionBehaviour());
    }

    public static Behavior<SessionMessage> create(
            GameConfig config,
            List<ActorRef<ParticipantMessage>> participants,
            Map<ActorRef<ParticipantMessage>, ParticipantType> knownTypes,
            ActorRef<GameResult> resultRef) {
        return Behaviors.setup(
                context -> {
                    Session session = new Session(context, config, participants, resultRef);
                    knownTypes.forEach(session::registerType);
                    return session.createSessionBehaviour();
                });
    }

    private Behavior<SessionMessage> onPlayTurn(PlayTurn playTurn) {
        ActorRef<ParticipantMessage> player = playTurn.replyTo();

        if (ending) {
            context.getLog().warn("Ignoring PlayTurn after session end from {}", player.path().name());
            return Behaviors.same();
        }

        if (playTurn.turn() != currentTurn) {
            context.getLog()
                    .warn(
                            "Wrong turn PlayTurn from {} (got {}, expected {})",
                            player.path().name(),
                            playTurn.turn(),
                            currentTurn);
            return Behaviors.same();
        }

        if (turnContributions.containsKey(player)) {
            context.getLog()
                    .warn(
                            "Duplicate PlayTurn from {} for turn {}",
                            player.path().name(),
                            playTurn.turn());
            return Behaviors.same();
        }

        if (!balances.containsKey(player)) {
            context.getLog().warn("Unknown participant PlayTurn from {}", player.path().name());
            return Behaviors.same();
        }

        double contribution = playTurn.pot();
        if (contribution < 0) {
            context.getLog().warn("Negative contribution from {}; treating as 0", player.path().name());
            contribution = 0;
        }

        double balance = balances.get(player);
        if (contribution > balance) {
            context.getLog()
                    .warn(
                            "Contribution {} exceeds balance {} for {}; clamping",
                            contribution,
                            balance,
                            player.path().name());
            contribution = balance;
        }

        context.getLog()
                .debug(
                        "Participant {} joined for turn {} with {}",
                        player.path().name(),
                        playTurn.turn(),
                        contribution);

        balances.put(player, balance - contribution);
        currentPot += contribution;
        turnContributions.put(player, contribution);

        if (turnContributions.size() == participants.size()) {
            double amountToShare = (currentPot * config.multiplicador()) / participants.size();
            expectedTotalWealth += currentPot * (config.multiplicador() - 1.0);

            Map<ActorRef<ParticipantMessage>, Double> contributionsSnapshot =
                    Map.copyOf(turnContributions);

            context.getSelf()
                    .tell(
                            new ShareReturnPotWithParticipants(
                                    context.getSelf(),
                                    participants,
                                    amountToShare,
                                    currentTurn,
                                    contributionsSnapshot));

            currentPot = 0;
            turnContributions.clear();
        }
        return Behaviors.same();
    }

    private Behavior<SessionMessage> onShareReturn(ShareReturnPotWithParticipants share) {
        for (ActorRef<ParticipantMessage> participant : participants) {
            balances.put(participant, balances.get(participant) + share.returnedAmount());
            participant.tell(
                    new PotReturned(
                            share.session(),
                            share.turn(),
                            share.returnedAmount(),
                            share.contributions()));
        }

        context.getLog().debug("Turn {} complete; share={}", share.turn(), share.returnedAmount());

        if (share.turn() + 1 >= config.turnos()) {
            ending = true;
            context.getSelf().tell(new EndSession());
        } else {
            currentTurn = share.turn() + 1;
        }
        return Behaviors.same();
    }

    private Behavior<SessionMessage> onSessionEnded() {
        participants.forEach(participant -> participant.tell(new SessionEnded()));

        double actualTotal = balances.values().stream().mapToDouble(Double::doubleValue).sum();
        boolean ok = Math.abs(actualTotal - expectedTotalWealth) < 1e-6;
        if (!ok) {
            context.getLog()
                    .error(
                            "Balance check failed: actualTotal={} expectedTotal={}",
                            actualTotal,
                            expectedTotalWealth);
        } else {
            context.getLog().info("Balance check OK: totalWealth={}", actualTotal);
        }

        List<PlayerOutcome> outcomes = new ArrayList<>();
        for (ActorRef<ParticipantMessage> p : participants) {
            outcomes.add(
                    new PlayerOutcome(
                            p.path().name(),
                            types.getOrDefault(p, ParticipantType.SANTO),
                            balances.get(p)));
        }

        resultRef.tell(new GameResult(config, List.copyOf(outcomes), actualTotal, ok));
        return Behaviors.stopped();
    }
}

package dev.freireservices.social_altruism.chat.participant;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.GameConfig;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.*;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Participant {

    private final ActorContext<ParticipantMessage> context;
    private ActorRef<PotRoomMessage> chatRoom;
    private ActorRef<SessionMessage> session;

    private int currentTurn;
    private double participantCoins;
    private final double initialCoins;
    private List<ActorRef<ParticipantMessage>> participants;
    private GameConfig config;
    private final ParticipantType participantType;

    /** null before any turn; after each PotReturned holds previous contributions. */
    private Map<ActorRef<ParticipantMessage>, Double> previousContributions;

    private Participant(
            ActorContext<ParticipantMessage> context,
            double participantCoins,
            ParticipantType participantType) {
        this.context = context;
        this.participantCoins = participantCoins;
        this.initialCoins = participantCoins;
        this.participantType = participantType;
        this.previousContributions = null;
    }

    public static Behavior<ParticipantMessage> create(
            double initialCoins, ParticipantType participantType) {
        return Behaviors.setup(
                ctx -> new Participant(ctx, initialCoins, participantType).behavior());
    }

    public static Behavior<ParticipantMessage> create(
            GameConfig config, ParticipantType participantType) {
        return create(config.monedasIniciales(), participantType);
    }

    private Behavior<ParticipantMessage> behavior() {
        return uninitialized();
    }

    private Behavior<ParticipantMessage> uninitialized() {
        return Behaviors.receive(ParticipantMessage.class)
                .onMessage(SessionDenied.class, this::onSessionDenied)
                .onMessage(
                        SessionGranted.class,
                        x -> {
                            onSessionGranted(x);
                            return readyToStart();
                        })
                .build();
    }

    private Behavior<ParticipantMessage> readyToStart() {
        return Behaviors.receive(ParticipantMessage.class)
                .onMessage(
                        SessionStarted.class,
                        x -> {
                            onSessionStarted(x);
                            return started();
                        })
                .build();
    }

    private Behavior<ParticipantMessage> started() {
        return Behaviors.receive(ParticipantMessage.class)
                .onMessage(PotReturned.class, this::onPotReturned)
                .onMessage(SessionEnded.class, this::onSessionEnded)
                .build();
    }

    private Behavior<ParticipantMessage> onSessionEnded(SessionEnded sessionEnded) {
        context.getLog()
                .info(
                        "Session ended for user: {}. Earned {} coins, profit {} %",
                        context.getSelf().path().name(),
                        String.format("%.3f", getParticipantCoins() - initialCoins),
                        calculateProfit());
        return Behaviors.stopped();
    }

    private Behavior<ParticipantMessage> onSessionDenied(SessionDenied message) {
        context.getLog().info("cannot start chat room session: {}", message.reason());
        return Behaviors.stopped();
    }

    private void onSessionGranted(SessionGranted message) {
        context.getLog()
                .info("Session granted message received for {} ", context.getSelf().path().name());
        this.chatRoom = message.chatRoom();
        this.session = message.session();
    }

    private void onSessionStarted(SessionStarted startSession) {
        context.getLog()
                .info(
                        "Session started for {} with {} participants",
                        context.getSelf().path().name(),
                        startSession.participants().size());
        this.currentTurn = 0;
        this.participants = startSession.participants();
        this.config = startSession.config();
        this.previousContributions = null;
        playTurn(startSession.replyTo());
    }

    private void playTurn(ActorRef<SessionMessage> replyTo) {
        // Zero-coin players stay in the game and still submit a (0) contribution.
        if (currentTurn >= config.turnos()) {
            return;
        }

        double aporte = decideContribution();
        SessionProtocol.PlayTurn message =
                new SessionProtocol.PlayTurn(
                        replyTo, context.getSelf(), participants, currentTurn, aporte);

        Duration delay = config.retardoTurno();
        if (delay != null && !delay.isZero() && !delay.isNegative()) {
            context.scheduleOnce(delay, replyTo, message);
        } else {
            replyTo.tell(message);
        }
    }

    /**
     * Contribute min(aportePorTurno, coins) when the strategy says yes; else 0.
     * Collaborated ≡ aporte &gt; 0.
     */
    private double decideContribution() {
        boolean collaborate =
                switch (participantType) {
                    case PICARO -> false;
                    case SANTO -> true;
                    case JUSTICIERO ->
                            previousContributions == null
                                    || previousContributions.values().stream()
                                            .allMatch(c -> c > 0);
                };

        if (!collaborate || participantCoins <= 0) {
            return 0;
        }

        double aporte = Math.min(config.aportePorTurno(), participantCoins);
        participantCoins -= aporte;
        return aporte;
    }

    private Behavior<ParticipantMessage> onPotReturned(PotReturned potReturned) {
        context.getLog()
                .debug(
                        "Pot returned: {} for participant {} turn {}",
                        String.format("%.2f", potReturned.returnedAmount()),
                        context.getSelf().path().name(),
                        potReturned.turn());

        participantCoins += potReturned.returnedAmount();
        previousContributions = potReturned.contributions();
        currentTurn = potReturned.turn() + 1;

        context.getLog()
                .debug(
                        "Player {} has now {} coins; started with {} for a partial profit of: {} %",
                        context.getSelf().path().name(),
                        String.format("%.3f", participantCoins),
                        initialCoins,
                        calculateProfit());

        if (currentTurn < config.turnos()) {
            playTurn(potReturned.session());
        }
        return Behaviors.same();
    }

    public double getParticipantCoins() {
        return participantCoins;
    }

    private double calculateProfit() {
        if (initialCoins == 0) {
            return 0;
        }
        return Math.round((participantCoins * 100) / initialCoins - 100);
    }
}

package dev.freireservices.social_altruism.chat.participant;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static dev.freireservices.social_altruism.chat.participant.ParticipantType.JUSTICIERO;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.*;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;

import java.security.SecureRandom;
import java.sql.Time;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.freireservices.social_altruism.chat.potroom.SessionProtocol;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Participant {

    private final ActorContext<ParticipantMessage> context;
    private ActorRef<PotRoomMessage> chatRoom;
    private ActorRef<SessionMessage> session;

    private boolean collaborateSwitch;
    private int currentTurn;
    private double participantCoins;
    private final double initialCoins;
    private List<ActorRef<ParticipantMessage>> participants;
    private int totalTurns;
    private final ParticipantType participantType;

    private Participant(
            ActorContext<ParticipantMessage> context,
            double participantCoins,
            ParticipantType participantType) {
        this.context = context;
        this.participantCoins = participantCoins;
        this.initialCoins = participantCoins;
        this.participantType = participantType;
        this.collaborateSwitch = participantType == JUSTICIERO || participantType == SANTO;
    }

    public static Behavior<ParticipantMessage> create(
            int initialCoins, ParticipantType participantType) {
        return Behaviors.setup(ctx -> new Participant(ctx, initialCoins, participantType).behavior());
    }

    public void decrementCoins(double coins) {
        this.participantCoins -= coins;
    }

    public void incrementCoins(double coins) {
        this.participantCoins += coins;
    }

    private Behavior<ParticipantMessage> behavior() {
        return Behaviors.receive(ParticipantMessage.class)
                .onMessage(SessionStarted.class, this::onSessionStarted)
                .onMessage(SessionDenied.class, this::onSessionDenied)
                .onMessage(SessionGranted.class, this::onSessionGranted)
                .onMessage(PotReturned.class, this::onPotReturned)
                .onMessage(SessionEnded.class, this::onSessionEnded)
                .build();
    }

    private Behavior<ParticipantMessage> onSessionEnded(SessionEnded sessionEnded) {

        context.getLog().info("Session ended for user {}", context.getSelf().path().name());
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Behaviors.stopped();
    }

    private Behavior<ParticipantMessage> onSessionDenied(
            SessionDenied message) {
        context.getLog().info("cannot start chat room session: {}", message.reason());
        return Behaviors.stopped();
    }

    private Behavior<ParticipantMessage> onSessionGranted(
            SessionGranted message) {
        setChatRoom(message.chatRoom());
        setSession(message.session());
        return Behaviors.same();
    }

    private Behavior<ParticipantMessage> onSessionStarted(
            SessionStarted startSession) {
        resetCurrentTurn();
        setParticipants(startSession.participants());
        setTotalTurns(startSession.totalTurns());
        playTurn(startSession.replyTo());
        context.getLog().info("Session started for {}  with {} participants", context.getSelf().path().name(), startSession.participants().size());
        return Behaviors.same();
    }

    private void playTurn(ActorRef<SessionMessage> replyTo) {
        if (getParticipantCoins() > 0 && getCurrentTurn() < getTotalTurns()) {
            replyTo.tell(new SessionProtocol.PlayTurn(
                    replyTo,
                    context.getSelf(),
                    participants,
                    getCurrentTurn(),
                    getParticipationForCurrentTurn()
            ));
        }
    }

    private double getParticipationForCurrentTurn() {
        var currentTurnCoins = getRandomNumberBetween(0, Math.floor(getParticipantCoins()));
        decrementCoins(currentTurnCoins);
        return currentTurnCoins;
    }

    public static double getRandomNumberBetween(double min, double max) {
        SecureRandom secureRandom = new SecureRandom();
        return Math.round(secureRandom.nextDouble(max - min) + min);
    }

    private Behavior<ParticipantMessage> onPotReturned(
            PotReturned potReturned) {
        context.getLog().info("Pot returned: {} for participant {}", potReturned.returnedAmount(), potReturned.participant().path().name());
        incrementCoins(potReturned.returnedAmount());
        incrementCurrentTurn();
        context
                .getLog()
                .info(
                        "Player {} has now {} coins; started with {} for a partial profit of: {} %",
                        potReturned.participant().path().name(),
                        getParticipantCoins(),
                        getInitialCoins(),
                        calculateProfit());

        // Still game?
        if (getParticipantCoins() > 1) {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            playTurn(potReturned.session());
        } else {
            context
                    .getLog()
                    .info(
                            "Player {} has now {} coins; started with {} for a total profit of: {} %",
                            potReturned.participant().path().name(),
                            getParticipantCoins(),
                            getInitialCoins(),
                            calculateProfit());

            context.getLog().info("END GAME");
            context.getLog().info("---------");
            context.getLog().info("END GAME");
            return Behaviors.stopped();
        }
        adjustBehaviour(potReturned);
        return Behaviors.same();
    }

    private void adjustBehaviour(PotReturned potReturned) {

        switch (participantType) {
            case SANTO:
                setCollaborateSwitch(true);
                break;
            case PICARO:
                setCollaborateSwitch(false);
                break;
            case JUSTICIERO:
                // Tweak minimum amount to collaborate
                setCollaborateSwitch(potReturned.returnedAmount() > participants.size());
                break;
        }
    }

    public void resetCurrentTurn() {
        setCurrentTurn(0);
    }

    public void incrementCurrentTurn() {
        setCurrentTurn(++this.currentTurn);
    }

    private double calculateProfit() {
        return Math.round((getParticipantCoins() * 100) / getInitialCoins() - 100);
    }
}

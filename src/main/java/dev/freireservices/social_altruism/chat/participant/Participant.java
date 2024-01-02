package dev.freireservices.social_altruism.chat.participant;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static dev.freireservices.social_altruism.chat.participant.ParticipantType.JUSTICIERO;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;

import java.security.SecureRandom;

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
    private double coins;
    private final double initialCoins;
    private int participantNumber;
    private int totalTurns;
    private final ParticipantType participantType;

    private Participant(
            ActorContext<ParticipantMessage> context,
            double coins,
            ParticipantType participantType) {
        this.context = context;
        this.coins = coins;
        this.initialCoins = coins;
        this.participantType = participantType;
        this.collaborateSwitch = participantType == JUSTICIERO || participantType == SANTO;
    }

    public static Behavior<ParticipantMessage> create(
            int initialCoins, ParticipantType participantType) {
        return Behaviors.setup(ctx -> new Participant(ctx, initialCoins, participantType).behavior());
    }

    public void decrementCoins(double coins) {
        this.coins -= coins;
    }

    public void incrementCoins(double coins) {
        this.coins += coins;
    }

    private Behavior<ParticipantMessage> behavior() {
        return Behaviors.receive(ParticipantMessage.class)
                .onMessage(ParticipantProtocol.SessionStarted.class, this::onSessionStarted)
                .onMessage(ParticipantProtocol.SessionDenied.class, this::onSessionDenied)
                .onMessage(ParticipantProtocol.SessionGranted.class, this::onSessionGranted)
                .onMessage(ParticipantProtocol.PotReturned.class, this::onPotReturned)
                .build();
    }

    private Behavior<ParticipantMessage> onSessionDenied(
            ParticipantProtocol.SessionDenied message) {
        context.getLog().info("cannot start chat room session: {}", message.reason());
        return Behaviors.stopped();
    }

    private Behavior<ParticipantMessage> onSessionGranted(
            ParticipantProtocol.SessionGranted message) {
        setChatRoom(message.chatRoom());
        setSession(message.session());
        return Behaviors.same();
    }

    private Behavior<ParticipantMessage> onSessionStarted(
            ParticipantProtocol.SessionStarted startSession) {
        resetCurrentTurn();
        setParticipantNumber(startSession.participants().size());
        setTotalTurns(startSession.totalTurns());
        playTurn(startSession.replyTo());
        context.getLog().info("Session started for {}  with {} participants", context.getSelf().path().name(), startSession.participants().size());
        return Behaviors.same();
    }

    private void playTurn(ActorRef<SessionMessage> replyTo) {
        if (getCoins() > 0 && getCurrentTurn() < getTotalTurns()) {
            replyTo.tell(new SessionProtocol.PlayTurn(
                    replyTo,
                    context.getSelf(),
                    getCurrentTurn(),
                    getParticipationForCurrentTurn()
            ));
            incrementCurrentTurn();
        }
    }

    private double getParticipationForCurrentTurn() {
        var currentTurnCoins = getRandomNumberBetween(0, Math.floor(getCoins()));
        decrementCoins(currentTurnCoins);
        return currentTurnCoins;
    }

    public static double getRandomNumberBetween(double min, double max) {
        SecureRandom secureRandom = new SecureRandom();
        return Math.round(secureRandom.nextDouble(max - min) + min);
    }

    private Behavior<ParticipantMessage> onPotReturned(
            ParticipantProtocol.PotReturned potReturned) {
        context.getLog().info("Pot returned: {}", potReturned.returnedAmount());
        incrementCoins(potReturned.returnedAmount());

        context
                .getLog()
                .info(
                        "Player {} has now {} coins; started with {} for a total profit of: {} %",
                        potReturned.participant().path().name(),
                        getCoins(),
                        getInitialCoins(),
                        calculateProfit());

        // Still game?
        if (getCoins() > 0) {
            playTurn(potReturned.session());
        } else {
            context
                    .getLog()
                    .info(
                            "Player {} has now {} coins; started with {} for a total profit of: {} %",
                            potReturned.participant().path().name(),
                            getCoins(),
                            getInitialCoins(),
                            calculateProfit());

            return Behaviors.stopped();
        }
        adjustBehaviour(potReturned);
        return Behaviors.same();
    }

    private void adjustBehaviour(ParticipantProtocol.PotReturned potReturned) {

        switch (participantType) {
            case SANTO:
                setCollaborateSwitch(true);
                break;
            case PICARO:
                setCollaborateSwitch(false);
                break;
            case JUSTICIERO:
                // Tweak minimum amount to collaborate
                setCollaborateSwitch(potReturned.returnedAmount() > getParticipantNumber());
                break;
        }
    }

    public void participant() {
        ActorRef<SessionProtocol.ParticipateInTurn> handle;
        // return this;
    }

    public void resetCurrentTurn() {
        setCurrentTurn(0);
    }

    public void incrementCurrentTurn() {
        setCurrentTurn(this.currentTurn++);
    }

    private double calculateProfit() {
        return Math.round((getCoins() * 100) / getInitialCoins() - 100);
    }
}

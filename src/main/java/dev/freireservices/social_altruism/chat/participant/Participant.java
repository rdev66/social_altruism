package dev.freireservices.social_altruism.chat.participant;

import static dev.freireservices.social_altruism.chat.participant.ParticipantType.*;
import static dev.freireservices.social_altruism.chat.participant.ParticipantType.JUSTICIERO;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.ParticipateInTurn;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.events.ParticipantProtocol;
import java.security.SecureRandom;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Participant {

  private final ActorContext<ParticipantProtocol.ParticipantMessage> context;
  private ActorRef<PotRoomMessage> chatRoom;

  private boolean collaborateSwitch;
  private int currentTurn;
  private double coins;
  private final double initialCoins;
  private int participantNumber;
  private int totalTurns;
  private final ParticipantType participantType;

  private Participant(
      ActorContext<ParticipantProtocol.ParticipantMessage> context,
      double coins,
      ParticipantType participantType) {
    this.context = context;
    this.coins = coins;
    this.initialCoins = coins;
    this.participantType = participantType;
    this.collaborateSwitch = participantType == JUSTICIERO || participantType == SANTO;
  }

  public static Behavior<ParticipantProtocol.ParticipantMessage> create(
      int initialCoins, ParticipantType participantType) {
    return Behaviors.setup(ctx -> new Participant(ctx, initialCoins, participantType).behavior());
  }

  public void decrementCoins(double coins) {
    this.coins -= coins;
  }

  public void incrementCoins(double coins) {
    this.coins += coins;
  }

  private Behavior<ParticipantProtocol.ParticipantMessage> behavior() {
    return Behaviors.receive(ParticipantProtocol.ParticipantMessage.class)
        .onMessage(ParticipantProtocol.SessionStarted.class, this::onSessionStarted)
        .onMessage(ParticipantProtocol.SessionDenied.class, this::onSessionDenied)
        .onMessage(ParticipantProtocol.SessionGranted.class, this::onSessionGranted)
        .onMessage(ParticipantProtocol.PotReturned.class, this::onPotReturned)
        .build();
  }

  private Behavior<ParticipantProtocol.ParticipantMessage> onSessionDenied(
      ParticipantProtocol.SessionDenied message) {
    context.getLog().info("cannot start chat room session: {}", message.reason());
    return Behaviors.stopped();
  }

  private Behavior<ParticipantProtocol.ParticipantMessage> onSessionGranted(
      ParticipantProtocol.SessionGranted message) {
    setChatRoom(message.chatRoom());
    return Behaviors.same();
  }

  private Behavior<ParticipantProtocol.ParticipantMessage> onSessionStarted(
      ParticipantProtocol.SessionStarted startSession) {
    resetCurrentTurn();
    setParticipantNumber(startSession.participants().size());
    playTurn(startSession.replyTo());

    context.getLog().info("Session started with {} participants", startSession.participants());
    return Behaviors.same();
  }

  private void playTurn(ActorRef<PotRoomMessage> replyTo) {
    if (getCoins() > 0 && getCurrentTurn() < getTotalTurns()) {
      replyTo.tell(new PotRoomProtocol.PlayTurn(context.getSelf(), getCurrentTurnParticipation()));
      incrementCurrentTurn();
    }
  }

  private double getCurrentTurnParticipation() {
    var currentTurnCoins = getRandomNumberBetween(0, Math.floor(getCoins()));
    decrementCoins(currentTurnCoins);
    return currentTurnCoins;
  }

  public static double getRandomNumberBetween(double min, double max) {
    SecureRandom secureRandom = new SecureRandom();
    return secureRandom.nextDouble(max - min) + min;
  }

  private Behavior<ParticipantProtocol.ParticipantMessage> onPotReturned(
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
      playTurn(potReturned.handle());
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
    ActorRef<ParticipateInTurn> handle;
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

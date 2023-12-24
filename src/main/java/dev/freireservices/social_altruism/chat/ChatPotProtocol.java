package dev.freireservices.social_altruism.chat;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.emptyList;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.commands.Commands;
import dev.freireservices.social_altruism.chat.events.Events;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ChatPotProtocol {
  private final ActorContext<Commands.RoomCommand> context;

  private final List<ActorRef<SessionCommand>> sessions;

  public double getCurrentPot() {
    return currentPot;
  }

  public void resetPot() {
    this.currentPot = 0;
  }

  public void addToPot(double pot) {
    this.currentPot += pot;
  }

  private double currentPot = 0.0;

  public int getCurrentTurn() {
    return currentTurn;
  }

  public int incrementCurrentTurnAndGet() {
    this.currentTurn++;
    return this.currentTurn;
  }

  private int currentTurn = 0;
  private int totalTurns = 0;

  public int getParticipantsInTurn() {
    return participantsInTurn;
  }

  public void incrementParticipantsInTurn() {
    this.participantsInTurn++;
  }

  public void resetParticipantsInTurn() {
    this.participantsInTurn = 0;
  }

  public int getNumberOfParticipants() {
    return numberOfParticipants;
  }

  private int participantsInTurn = 0;
  private final int numberOfParticipants;

  private ChatPotProtocol(
      ActorContext<Commands.RoomCommand> context,
      List<ActorRef<SessionCommand>> sessions,
      int numberOfParticipants,
      int turns) {
    this.context = context;
    this.sessions = sessions;
    this.numberOfParticipants = numberOfParticipants;
    this.totalTurns = turns;
  }

  // #chatroom-behavior
  // #chatroom-protocol

  private Behavior<Commands.RoomCommand> onGetPotSession(Commands.EnterPot enterPot) {

    // Add check session started
    if (sessions.stream()
        .anyMatch(
            s ->
                s.path()
                    .name()
                    .equals(URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8)))) {

      enterPot.replyTo().tell(new Events.SessionDenied("Can only enter a pot once"));
    }

    context.getLog().info("Participant joined {} pot", enterPot.replyTo().path().name());

    ActorRef<Events.SessionEvent> client = enterPot.replyTo();

    ActorRef<SessionCommand> session =
        context.spawn(
            Session.create(client), URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8));

    // narrow to only expose PostMessage
    client.tell(new Events.SessionGranted(session.narrow()));

    sessions.add(session);

    if (numberOfParticipants == sessions.size()) {
      context.getLog().info("All participants joined; pot is ready to start.");
      return createPotBehaviour();
    } else {
      // Waiting for more participants
      context.getLog().info("Waiting for more participants.");
      return Behaviors.same();
    }
  }

  private Behavior<Commands.RoomCommand> onPlayTurn(Commands.PlayTurn playTurn) {

    context
        .getLog()
        .info(
            "Participant {} joined for turn {} with {}",
            playTurn.replyTo().path().name(),
            currentTurn,
            playTurn.pot());

    // Add to current pot
    addToPot(playTurn.pot());
    incrementParticipantsInTurn();

    if (getParticipantsInTurn() == numberOfParticipants) {

      double amountToShare = (getCurrentPot() * 2) / numberOfParticipants;
      sessions.forEach(s -> s.tell(new SharePotWithParticipants(amountToShare)));

      resetPot();
      resetParticipantsInTurn();

      context.getLog().info("Turn {} complete", getCurrentTurn());

      if (incrementCurrentTurnAndGet() == totalTurns) {
        context.getLog().info("All turns completed");
        return Behaviors.stopped();
      }
    }

    return Behaviors.same();
  }

  private Behavior<Commands.RoomCommand> createPotBehaviour() {
    return Behaviors.receive(Commands.RoomCommand.class)
        .onMessage(Commands.EnterPot.class, this::onGetPotSession)
        .onMessage(Commands.PlayTurn.class, this::onPlayTurn)
        .build();
  }

  public static Behavior<Commands.RoomCommand> create(int numberOfParticipants, int turns) {
    return Behaviors.setup(
        ctx ->
            new ChatPotProtocol(ctx, new ArrayList<>(), numberOfParticipants, turns)
                .createPotBehaviour());
  }

  interface SessionCommand {}

  public record ParticipateInTurn(String message) implements SessionCommand {}

  public record SharePotWithParticipants(double returnedAmount) implements SessionCommand {}

  static class Session {
    static Behavior<ChatPotProtocol.SessionCommand> create(ActorRef<Events.SessionEvent> client) {
      return Behaviors.receive(ChatPotProtocol.SessionCommand.class)
          .onMessage(
              SharePotWithParticipants.class,
              pot -> onSharePotWithParticipants(client, pot.returnedAmount))
          .build();
    }

    private static Behavior<SessionCommand> onSharePotWithParticipants(
        ActorRef<Events.SessionEvent> participant, double returnedAmount) {
      participant.tell(new Events.PotReturned(participant, returnedAmount));
      return Behaviors.same();
    }
  }
}

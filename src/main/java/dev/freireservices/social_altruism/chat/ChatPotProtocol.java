package dev.freireservices.social_altruism.chat;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.emptyList;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ChatPotProtocol {
  private final ActorContext<RoomCommand> context;

  public double getCurrentPot() {
    return currentPot;
  }

  public void setCurrentPot(double currentPot) {
    this.currentPot = currentPot;
  }

  public void addToPot(double pot) {
    this.currentPot += pot;
  }

  private double currentPot = 0.0;
  private int currentTurn = 0;
  private final int numberOfParticipants;

  private ChatPotProtocol(ActorContext<RoomCommand> context, int numberOfParticipants) {
    this.numberOfParticipants = numberOfParticipants;
    this.context = context;
  }

  interface RoomCommand {}

  public record EnterPot(ActorRef<SessionEvent> replyTo, double pot) implements RoomCommand {}

  // #chatroom-protocol
  // #chatroom-behavior
  private record UpdatePlayerAfterTurn(String screenName, String message) implements RoomCommand {}

  // #chatroom-behavior
  // #chatroom-protocol

  private Behavior<RoomCommand> onGetPotSession(
      EnterPot enterPot, List<ActorRef<SessionCommand>> sessions) {

    //Add check session started

    if (sessions.stream()
        .anyMatch(
            s ->
                s.path().name().equals(URLEncoder.encode(enterPot.replyTo.path().name(), UTF_8)))) {

      enterPot.replyTo.tell(new SessionDenied("Can only enter a pot once"));
    }

    context
        .getLog()
        .info("Participant joined {} turn pot: {}", enterPot.replyTo.path().name(), enterPot.pot);

    // Add to current pot
    addToPot(enterPot.pot);

    ActorRef<SessionEvent> client = enterPot.replyTo;

    ActorRef<SessionCommand> ses =
        context.spawn(
            Session.create(client), URLEncoder.encode(enterPot.replyTo.path().name(), UTF_8));

    // narrow to only expose PostMessage
    client.tell(new SessionGranted(ses.narrow()));

    List<ActorRef<SessionCommand>> newSessions = new ArrayList<>(sessions);

    newSessions.add(ses);

    if (numberOfParticipants == newSessions.size()) {
      // Begin
      context.getLog().info("All participants joined; beginning turn: " + getCurrentPot());

      double amountToShare = (getCurrentPot() * 2) / numberOfParticipants;

      context.getLog().info("Starting pot: " + getCurrentPot());

      newSessions.forEach(s -> s.tell(new SharePotWithParticipants(amountToShare)));

      currentTurn++;
      return Behaviors.same();

    } else {
      // Waiting for more participants
      context.getLog().info("Waiting for more participants.");

      return createPot(newSessions);
    }
  }

  private Behavior<RoomCommand> onUpdatePlayerAfterTurn(
      List<ActorRef<SessionCommand>> sessions, UpdatePlayerAfterTurn pub) {
    // NotifyClient notification = new NotifyClient((new MessagePosted(pub.screenName,
    // pub.message)));

    return Behaviors.same();
  }

  private Behavior<RoomCommand> createPot(List<ActorRef<SessionCommand>> sessions) {
    return Behaviors.receive(RoomCommand.class)
        .onMessage(EnterPot.class, enterPot -> onGetPotSession(enterPot, sessions))
        //
        .onMessage(UpdatePlayerAfterTurn.class, pub -> onUpdatePlayerAfterTurn(sessions, pub))
        .build();
  }

  public static Behavior<RoomCommand> create(int numberOfParticipants) {
    return Behaviors.setup(ctx -> new ChatPotProtocol(ctx, numberOfParticipants).createPot(emptyList()));
  }

  public interface SessionEvent {}

  public record SessionGranted(ActorRef<ParticipateInTurn> handle) implements SessionEvent {}

  public record SessionDenied(String reason) implements SessionEvent {}

  public record MessagePosted(String screenName, String message) implements SessionEvent {}

  public record PotReturned(double returnedAmount) implements SessionEvent {}

  interface SessionCommand {}

  public record ParticipateInTurn(String message) implements SessionCommand {}

  public record SharePotWithParticipants(double returnedAmount) implements SessionCommand {}

  static class Session {
    static Behavior<ChatPotProtocol.SessionCommand> create(ActorRef<SessionEvent> client) {
      return Behaviors.receive(ChatPotProtocol.SessionCommand.class)
          .onMessage(
              SharePotWithParticipants.class,
              pot -> onSharePotWithParticipants(client, pot.returnedAmount))
          .build();
    }

    private static Behavior<SessionCommand> onSharePotWithParticipants(
        ActorRef<SessionEvent> client, double returnedAmount) {
      client.tell(new ChatPotProtocol.PotReturned(returnedAmount));
      return Behaviors.same();
    }
  }
}

package dev.freireservices.social_altruism.chat.commands;

import akka.actor.typed.ActorRef;

import java.util.List;

import static dev.freireservices.social_altruism.chat.events.ParticipantProtocol.*;

public class PotRoomProtocol {
  public interface PotRoomMessage {}

  public record EnterPot(ActorRef<ParticipantMessage> replyTo)
      implements PotRoomMessage {}

  public record StartSession(
      ActorRef<ParticipantMessage> replyTo,
      List<ActorRef<PotRoomMessage>> sessions)
      implements PotRoomMessage {}

  public record ParticipateInTurn(String message) implements PotRoomMessage {}

  public record ShareReturnPotWithParticipants(double returnedAmount) implements PotRoomMessage {}

  public record PlayTurn(
      //ActorRef<PotRoomMessage> chatRoom,
      ActorRef<ParticipantMessage> replyTo,
      double pot)
      implements PotRoomMessage {}

  // #chatroom-protocol
  // #chatroom-behavior
}

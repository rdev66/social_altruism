package dev.freireservices.social_altruism.chat.events;

import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.PotRoomMessage;

import java.util.List;

public class ParticipantProtocol {
  public interface ParticipantMessage {}

  public record SessionGranted(
          ActorRef<PotRoomMessage> chatRoom,
          ActorRef<PotRoomProtocol.ParticipateInTurn> handle)
      implements ParticipantMessage {}

  public record SessionStarted(
          ActorRef<PotRoomMessage> replyTo,
          List<ActorRef<ParticipantMessage>> participants)
      implements ParticipantMessage {}

  public record SessionDenied(String reason) implements ParticipantMessage {}

  public record PotReturned(
      ActorRef<PotRoomMessage> handle,
      ActorRef<ParticipantMessage> participant,
      double returnedAmount)
      implements ParticipantMessage {}
}

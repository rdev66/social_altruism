package dev.freireservices.social_altruism.chat.potroom;

import akka.actor.typed.ActorRef;

import static dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;

public class PotRoomProtocol {
  public interface PotRoomMessage {}

  public record EnterPot(ActorRef<ParticipantMessage> replyTo)
      implements PotRoomMessage {}
}

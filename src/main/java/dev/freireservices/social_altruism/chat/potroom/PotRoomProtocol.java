package dev.freireservices.social_altruism.chat.potroom;

import org.apache.pekko.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;

import static dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;

public class PotRoomProtocol {
  public interface PotRoomMessage {}

  public record EnterPot(ActorRef<ParticipantMessage> replyTo, ParticipantType type)
      implements PotRoomMessage {}
}

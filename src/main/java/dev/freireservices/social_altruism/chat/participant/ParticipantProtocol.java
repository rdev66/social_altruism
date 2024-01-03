package dev.freireservices.social_altruism.chat.participant;

import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;

import java.util.List;

public class ParticipantProtocol {
    public interface ParticipantMessage {
    }

    enum Timeout implements ParticipantMessage {
        INSTANCE
    }

    public record SessionGranted(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> session
            )
            implements ParticipantMessage {
    }

    public record SessionStarted(
            ActorRef<PotRoomMessage> chatRoom,

            ActorRef<SessionMessage> replyTo,
            List<ActorRef<ParticipantMessage>> participants,
            int totalTurns)
            implements ParticipantMessage {
    }

    public record SessionDenied(String reason) implements ParticipantMessage {
    }

    public record SessionEnded() implements ParticipantMessage {
    }

    public record PotReturned(
            ActorRef<SessionMessage> session,
            ActorRef<ParticipantMessage> participant,
            double returnedAmount)
            implements ParticipantMessage {
    }
}

package dev.freireservices.social_altruism.chat.potroom;

import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import lombok.Getter;

import java.util.List;

@Getter
public class SessionProtocol {

    public interface SessionMessage {
    }

    public record StartSession(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> replyTo,
            List<ActorRef<ParticipantMessage>> participants)
            implements SessionMessage {
    }

    public record ParticipateInTurn(String message) implements SessionMessage {
    }

    public record ShareReturnPotWithParticipants(
            ActorRef<SessionMessage> session,
            ActorRef<ParticipantMessage> participant,
            double returnedAmount) implements SessionMessage {
    }

    public record PlayTurn(
            ActorRef<SessionMessage> session,
            ActorRef<ParticipantMessage> replyTo,
            int turn,
            double pot)
            implements SessionMessage {
    }

}
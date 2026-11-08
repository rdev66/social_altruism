package dev.freireservices.social_altruism.chat.potroom;

import org.apache.pekko.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;

import java.util.List;

public class SessionProtocol {

    public interface SessionMessage {
    }

    public record PlayTurn(
            ActorRef<SessionMessage> session,
            ActorRef<ParticipantMessage> replyTo,
            List<ActorRef<ParticipantMessage>> participants,
            int turn,
            double pot)
            implements SessionMessage {
    }

    public record ShareReturnPotWithParticipants(
            ActorRef<SessionMessage> session,
            List<ActorRef<ParticipantMessage>> participants,
            double returnedAmount,
            int turn,
            java.util.Map<ActorRef<ParticipantMessage>, Double> contributions)
            implements SessionMessage {
    }

    public record EndSession() implements SessionMessage {
    }
}

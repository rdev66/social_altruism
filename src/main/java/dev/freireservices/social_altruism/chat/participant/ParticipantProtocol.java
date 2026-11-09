package dev.freireservices.social_altruism.chat.participant;

import dev.freireservices.social_altruism.chat.GameConfig;
import org.apache.pekko.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;

import java.util.List;
import java.util.Map;

public class ParticipantProtocol {
    public interface ParticipantMessage {
    }

    public record SessionGranted(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> session)
            implements ParticipantMessage {
    }

    public record SessionStarted(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> replyTo,
            List<ActorRef<ParticipantMessage>> participants,
            GameConfig config)
            implements ParticipantMessage {
    }

    public record SessionDenied(String reason) implements ParticipantMessage {
    }

    public record SessionEnded() implements ParticipantMessage {
    }

    /** Session asks the participant to report its local coin balance. */
    public record RequestFinalBalance(ActorRef<SessionMessage> session)
            implements ParticipantMessage {
    }

    /**
     * Turn settlement: equal share of pot×multiplier, plus the contribution map for strategies.
     */
    public record PotReturned(
            ActorRef<SessionMessage> session,
            int turn,
            double returnedAmount,
            Map<ActorRef<ParticipantMessage>, Double> contributions)
            implements ParticipantMessage {
    }
}

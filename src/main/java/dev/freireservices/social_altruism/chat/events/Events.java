package dev.freireservices.social_altruism.chat.events;

import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.ChatPotProtocol;

public class Events {
    public interface SessionEvent {}

    public record SessionGranted(ActorRef<ChatPotProtocol.ParticipateInTurn> handle) implements SessionEvent {}

    public record SessionDenied(String reason) implements SessionEvent {}

    public record PotReturned(ActorRef participant, double returnedAmount) implements SessionEvent {}


}

package dev.freireservices.social_altruism.chat.commands;

import akka.actor.typed.ActorRef;
import dev.freireservices.social_altruism.chat.events.Events;

public class Commands {
    public interface RoomCommand {}

    public record EnterPot(ActorRef<Events.SessionEvent> replyTo) implements RoomCommand {}

    public record PlayTurn(ActorRef<Events.SessionEvent> replyTo, double pot) implements RoomCommand {}

    // #chatroom-protocol
    // #chatroom-behavior
}

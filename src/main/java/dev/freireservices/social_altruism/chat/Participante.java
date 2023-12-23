package dev.freireservices.social_altruism.chat;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

public class Participante {

  public static Behavior<ChatPotProtocol.SessionEvent> create(int monedasInit) {
    return Behaviors.setup(ctx -> new Participante(ctx, monedasInit).behavior());
  }

  private final ActorContext<ChatPotProtocol.SessionEvent> context;

  public double getMonedas() {
    return monedas;
  }

  public void setMonedas(double monedas) {
    this.monedas = monedas;
  }

  public void decrementMonedas(double monedas) {
    this.monedas -= monedas;
  }

  public void incrementMonedas(double monedas) {
    this.monedas += monedas;
  }

  private double monedas;

  private Participante(ActorContext<ChatPotProtocol.SessionEvent> context, double monedas) {
    this.context = context;
    this.monedas = monedas;
  }

  private Behavior<ChatPotProtocol.SessionEvent> behavior() {
    return Behaviors.receive(ChatPotProtocol.SessionEvent.class)
        .onMessage(ChatPotProtocol.SessionDenied.class, this::onSessionDenied)
        .onMessage(ChatPotProtocol.SessionGranted.class, this::onSessionGranted)
        .onMessage(ChatPotProtocol.MessagePosted.class, this::onMessagePosted)
        .onMessage(ChatPotProtocol.PotReturned.class, this::onPotReturned)
        .build();
  }

  private Behavior<ChatPotProtocol.SessionEvent> onSessionDenied(
      ChatPotProtocol.SessionDenied message) {
    context.getLog().info("cannot start chat room session: {}", message.reason());
    return Behaviors.stopped();
  }

  private Behavior<ChatPotProtocol.SessionEvent> onSessionGranted(
      ChatPotProtocol.SessionGranted message) {
    return Behaviors.same();
  }

  private Behavior<ChatPotProtocol.SessionEvent> onMessagePosted(
      ChatPotProtocol.MessagePosted message) {
    context
        .getLog()
        .info("message has been posted by '{}': {}", message.screenName(), message.message());
    return Behaviors.same();
  }

  private Behavior<ChatPotProtocol.SessionEvent> onPotReturned(
      ChatPotProtocol.PotReturned potReturned) {
    context.getLog().info("Pot returned: {}", potReturned.returnedAmount());
    incrementMonedas(potReturned.returnedAmount());
    return Behaviors.same();
  }
}

package dev.freireservices.social_altruism.chat;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.events.Events;

public class Participante {

  public static Behavior<Events.SessionEvent> create(int monedasInit) {
    return Behaviors.setup(ctx -> new Participante(ctx, monedasInit).behavior());
  }

  private final ActorContext<Events.SessionEvent> context;

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

  public double getMonedasInit() {
    return monedasInit;
  }

  private final double monedasInit;

  private Participante(ActorContext<Events.SessionEvent> context, double monedas) {
    this.context = context;
    this.monedas = monedas;
    this.monedasInit = monedas;
  }

  private Behavior<Events.SessionEvent> behavior() {
    return Behaviors.receive(Events.SessionEvent.class)
        .onMessage(Events.SessionDenied.class, this::onSessionDenied)
        .onMessage(Events.SessionGranted.class, this::onSessionGranted)
        .onMessage(Events.PotReturned.class, this::onPotReturned)
        .build();
  }

  private Behavior<Events.SessionEvent> onSessionDenied(Events.SessionDenied message) {
    context.getLog().info("cannot start chat room session: {}", message.reason());
    return Behaviors.stopped();
  }

  private Behavior<Events.SessionEvent> onSessionGranted(Events.SessionGranted message) {
    return Behaviors.same();
  }

  private Behavior<Events.SessionEvent> onPotReturned(Events.PotReturned potReturned) {
    context.getLog().info("Pot returned: {}", potReturned.returnedAmount());
    incrementMonedas(potReturned.returnedAmount());
    context
        .getLog()
        .info(
            "Player {} has now {} coins; started with {} for a total profit of: {} %",
            potReturned.participant().path().name(),
            getMonedas(),
            getMonedasInit(),
            calculateProfit());

    //Calcular contribución total.
    //Si detecta baja contribución aplicar penalización


    return Behaviors.same();
  }

  private double calculateProfit() {
    return Math.round((getMonedas() * 100) / getMonedasInit() -100);
  }
}

package dev.freireservices.social_altruism.chat.potroom;

import static java.nio.charset.StandardCharsets.*;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.ShareReturnPotWithParticipants;
import dev.freireservices.social_altruism.chat.commands.PotRoomProtocol.StartSession;
import dev.freireservices.social_altruism.chat.events.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.events.ParticipantProtocol.ParticipantMessage;
import lombok.Getter;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PotRoom {
  private final ActorContext<PotRoomMessage> context;
  private final List<ActorRef<PotRoomMessage>> participants;

  private double currentPot = 0.0;
  private int currentTurn = 0;
  private int totalTurns = 0;
  private int participantsInTurn = 0;
  private final int numberOfParticipants;

  private PotRoom(
      ActorContext<PotRoomMessage> context,
      List<ActorRef<PotRoomMessage>> participants,
      int numberOfParticipants,
      int turns) {
    this.context = context;
    this.participants = participants;
    this.numberOfParticipants = numberOfParticipants;
    this.totalTurns = turns;
  }

  public void resetPot() {
    this.currentPot = 0;
  }

  public void addToPot(double pot) {
    this.currentPot += pot;
  }

  public void incrementParticipantsInTurn() {
    this.participantsInTurn++;
  }

  public void resetParticipantsInTurn() {
    this.participantsInTurn = 0;
  }

  public int incrementCurrentTurnAndGet() {
    this.currentTurn++;
    return this.currentTurn;
  }

  // #chatroom-behavior
  // #chatroom-protocol

  private Behavior<PotRoomMessage> onGetPotSession(
      ActorRef<PotRoomMessage> chatRoom, PotRoomProtocol.EnterPot enterPot) {

    validate(enterPot);

    context.getLog().info("Participant joined {} pot", enterPot.replyTo().path().name());

    ActorRef<ParticipantMessage> client = enterPot.replyTo();

    ActorRef<PotRoomMessage> session =
        context.spawn(
            Session.create(chatRoom, client),
            URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8));

    client.tell(new ParticipantProtocol.SessionGranted(chatRoom,session.narrow()));

    participants.add(session);

    if (getNumberOfParticipants() == participants.size()) {
      context.getLog().info("All participants joined; pot is ready to start.");

      // Communicate session start and share pot info with all participants
      chatRoom.tell(new StartSession(enterPot.replyTo(), participants));

      return createPotBehaviour(chatRoom);
    } else {
      // Waiting for more participants
      context.getLog().info("Waiting for more participants.");
      return Behaviors.same();
    }
  }

  private void validate(PotRoomProtocol.EnterPot enterPot) {
    // Add check session started
    if (participants.stream()
        .anyMatch(
            s ->
                s.path()
                    .name()
                    .equals(URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8)))) {

      enterPot.replyTo().tell(new ParticipantProtocol.SessionDenied("Can only enter a pot once"));
    }
  }

  private Behavior<PotRoomMessage> onPlayTurn(PotRoomProtocol.PlayTurn playTurn) {
    context
        .getLog()
        .info(
            "Participant joined for turn {} with {}",
            //playTurn.replyTo().path().name(),
            currentTurn,
            playTurn.pot());

    // Add to current pot
    addToPot(playTurn.pot());
    incrementParticipantsInTurn();

    if (getParticipantsInTurn() == numberOfParticipants) {

      double amountToShare = (getCurrentPot() * 2) / numberOfParticipants;
      participants.forEach(s -> s.tell(new ShareReturnPotWithParticipants(amountToShare)));

      resetPot();
      resetParticipantsInTurn();

      context.getLog().info("Turn {} complete", getCurrentTurn());

      if (incrementCurrentTurnAndGet() == totalTurns) {
        context.getLog().info("All turns completed");
        return Behaviors.stopped();
      }
    }
    return Behaviors.same();
  }

  private Behavior<PotRoomMessage> createPotBehaviour(ActorRef<PotRoomMessage> chatRoom) {
    return Behaviors.receive(PotRoomMessage.class)
        .onMessage(PotRoomProtocol.EnterPot.class, x -> onGetPotSession(chatRoom, x))
        .onMessage(PotRoomProtocol.PlayTurn.class, this::onPlayTurn)
        .build();
  }

  public static Behavior<PotRoomMessage> create(int numberOfParticipants, int turns) {
    return Behaviors.setup(
        ctx ->
            new PotRoom(ctx, new ArrayList<>(), numberOfParticipants, turns)
                .createPotBehaviour(ctx.getSelf()));
  }

  static class Session {
    static Behavior<PotRoomMessage> create(
        ActorRef<PotRoomMessage> chatRoom, ActorRef<ParticipantMessage> client) {
      return Behaviors.receive(PotRoomMessage.class)
          .onMessage(
              ShareReturnPotWithParticipants.class,
              sharePot -> onSharePotWithParticipants(chatRoom, client, sharePot.returnedAmount()))
          .onMessage(
              StartSession.class,
              (startSession) -> onSessionStarted(startSession.replyTo(), startSession.sessions()))
          .build();
    }

    private static Behavior<PotRoomMessage> onSessionStarted(
        ActorRef<ParticipantMessage> replyTo, List<ActorRef<PotRoomMessage>> participants) {
      participants.forEach(s -> s.tell(new StartSession(replyTo, participants)));
      return Behaviors.same();
    }

    private static Behavior<PotRoomMessage> onSharePotWithParticipants(
        ActorRef<PotRoomMessage> chatRoom,
        ActorRef<ParticipantMessage> participant,
        double returnedAmount) {
      participant.tell(new ParticipantProtocol.PotReturned(chatRoom, participant, returnedAmount));
      return Behaviors.same();
    }
  }
}

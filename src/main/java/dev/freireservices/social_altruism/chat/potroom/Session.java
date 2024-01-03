package dev.freireservices.social_altruism.chat.potroom;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.PotReturned;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.SessionEnded;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.SessionStarted;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.*;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
public class Session {

    private final ActorContext<SessionMessage> context;
    private int currentTurn = 0;
    private final int totalTurns;
    private final List<ActorRef<ParticipantMessage>> participants;

    private double currentPot = 0.0;
    private int numberOfParticipantsInCurrentTurn;


    public Session(ActorContext<SessionMessage> context, List<ActorRef<ParticipantMessage>> participants, int totalTurns) {
        this.context = context;
        this.participants = participants;
        this.totalTurns = totalTurns;
    }


    public void resetPot() {
        this.currentPot = 0;
    }

    public void addToPot(double pot) {
        currentPot += pot;
    }

    public void incrementParticipantsInTurn() {
        this.numberOfParticipantsInCurrentTurn++;
    }

    public void resetNumberOfParticipantsInTurn() {
        this.numberOfParticipantsInCurrentTurn = 0;
    }

    public int incrementCurrentTurnAndGet() {
        return ++this.currentTurn;
    }


    public Behavior<SessionMessage> createSessionBehaviour() {
        return Behaviors.receive(SessionMessage.class)
                .onMessage(StartSession.class, startSession -> onSessionStarted(startSession.chatRoom()
                        , startSession.replyTo(), startSession.participants(), totalTurns))
                .onMessage(PlayTurn.class, this::onPlayTurn)
                .onMessage(EndSession.class, endSession -> onSessionEnded(participants))
                .onMessage(ShareReturnPotWithParticipants.class,
                        sharePot -> onReturnPotToParticipants(sharePot.session(), sharePot.participants(), sharePot.returnedAmount()))
                .build();
    }


    public static Behavior<SessionMessage> create(List<ActorRef<ParticipantMessage>> participants, int totalTurns) {
        return Behaviors.setup(context -> new Session(context, participants, totalTurns)
                .createSessionBehaviour());
    }


    private Behavior<SessionMessage> onPlayTurn(
            PlayTurn playTurn) {
        context.getLog()
                .info("Participant {} joined for turn {} with {}",
                        playTurn.replyTo().path().name(),
                        playTurn.turn(),
                        playTurn.pot());

        // Add to current pot
        addToPot(playTurn.pot());
        incrementParticipantsInTurn();

        if (getNumberOfParticipantsInCurrentTurn() == participants.size()) {

            double amountToShare = (getCurrentPot() * 2) / participants.size();

            playTurn.session().narrow().tell(new ShareReturnPotWithParticipants(playTurn.session(), playTurn.participants(), amountToShare));

            resetPot();
            resetNumberOfParticipantsInTurn();

            context.getLog().info("Turn {} complete", getCurrentTurn());

            if (incrementCurrentTurnAndGet() == totalTurns) {
                context.getLog().info("All turns completed");
                context.getLog().info("Waiting for other messages, then ending session.");
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                playTurn.session().narrow().tell(new EndSession());

            }
        }
        return Behaviors.same();
    }

    private Behavior<SessionMessage> onSessionEnded(List<ActorRef<ParticipantMessage>> participants) {
        participants.forEach(participant -> participant.tell(new SessionEnded()));
        return Behaviors.stopped();
    }

    private static Behavior<SessionMessage> onSessionStarted(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> session,
            List<ActorRef<ParticipantMessage>> participants,
            int totalTurns) {
        participants.forEach(s -> s.tell(new SessionStarted(chatRoom, session, participants, totalTurns)));
        return Behaviors.same();
    }

    private static Behavior<SessionMessage> onReturnPotToParticipants(
            ActorRef<SessionMessage> session,
            List<ActorRef<ParticipantMessage>> participants,
            double returnedAmount) {
        participants.forEach(participant -> participant.tell(new PotReturned(session, participant, returnedAmount)));
        return Behaviors.same();
    }

}

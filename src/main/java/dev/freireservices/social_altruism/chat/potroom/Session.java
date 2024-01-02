package dev.freireservices.social_altruism.chat.potroom;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;
import lombok.Getter;

import java.util.List;

@Getter
public class Session {

    private final ActorContext<SessionMessage> context;
    private int currentTurn = 0;
    private final int totalTurns;
    private final int numberOfParticipants;

    private double currentPot = 0.0;
    private int participantsInCurrentTurn;


    public Session(ActorContext<SessionMessage> context, int totalTurns, int numberOfParticipants) {
        this.context = context;
        this.totalTurns = totalTurns;
        this.numberOfParticipants = numberOfParticipants;
    }


    public void resetPot() {
        this.currentPot = 0;
    }

    public void addToPot(double pot) {
        currentPot += pot;
    }

    public void incrementParticipantsInTurn() {
        this.participantsInCurrentTurn++;
    }

    public void resetParticipantsInTurn() {
        this.participantsInCurrentTurn = 0;
    }

    public int incrementCurrentTurnAndGet() {
        return ++this.currentTurn;
    }


    public Behavior<SessionMessage> createSessionBehaviour() {
        return Behaviors.receive(SessionMessage.class)
                .onMessage(SessionProtocol.StartSession.class, startSession -> onSessionStarted(startSession.chatRoom()
                        , startSession.replyTo(), startSession.participants(), totalTurns))
                .onMessage(SessionProtocol.PlayTurn.class, this::onPlayTurn)
                .onMessage(SessionProtocol.ShareReturnPotWithParticipants.class,
                        sharePot -> onSharePotWithParticipant(sharePot.session(), sharePot.participant(), sharePot.returnedAmount()))
                .build();
    }


    public static Behavior<SessionMessage> create(int numberOfParticipants, int turns) {
        return Behaviors.setup(context -> new Session(context, numberOfParticipants, turns)
                .createSessionBehaviour());
    }


    private Behavior<SessionMessage> onPlayTurn(
            SessionProtocol.PlayTurn playTurn) {
        context.getLog()
                .info("Participant {} joined for turn {} with {}",
                        playTurn.replyTo().path().name(),
                        playTurn.turn(),
                        playTurn.pot());

        // Add to current pot
        addToPot(playTurn.pot());
        incrementParticipantsInTurn();

        if (getParticipantsInCurrentTurn() == numberOfParticipants) {

            double amountToShare = (getCurrentPot() * 2) / numberOfParticipants;

            playTurn.session().narrow().tell(new SessionProtocol.ShareReturnPotWithParticipants(playTurn.session(), playTurn.replyTo(), amountToShare));

            resetPot();
            resetParticipantsInTurn();

            context.getLog().info("Turn {} complete", getCurrentTurn());

            if (incrementCurrentTurnAndGet() == totalTurns) {
                context.getLog().info("All turns completed");
                //return Behaviors.stopped();
            }
        }
        return Behaviors.same();
    }

    private static Behavior<SessionMessage> onSessionStarted(
            ActorRef<PotRoomMessage> chatRoom,
            ActorRef<SessionMessage> session,
            List<ActorRef<ParticipantMessage>> participants,
            int totalTurns) {
        participants.forEach(s -> s.tell(new ParticipantProtocol.SessionStarted(chatRoom, session, participants, totalTurns)));
        return Behaviors.same();
    }

    private static Behavior<SessionMessage> onSharePotWithParticipant(
            ActorRef<SessionMessage> session,
            ActorRef<ParticipantMessage> participant,
            double returnedAmount) {
        participant.tell(new ParticipantProtocol.PotReturned(session, participant, returnedAmount));
        return Behaviors.same();
    }

}

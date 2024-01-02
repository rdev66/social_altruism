package dev.freireservices.social_altruism.chat.potroom;

import static dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.*;
import static java.nio.charset.StandardCharsets.*;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.*;
import dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.ParticipantMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;
import lombok.Getter;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PotRoom {
    private final ActorContext<PotRoomMessage> context;
    private final List<ActorRef<ParticipantMessage>> participants;

    private final int totalTurns;
    private final int numberOfParticipants;

    private PotRoom(
            ActorContext<PotRoomMessage> context,
            int numberOfParticipants,
            int totalTurns) {
        this.context = context;
        this.participants = new ArrayList<>();
        this.numberOfParticipants = numberOfParticipants;
        this.totalTurns = totalTurns;
    }

    private Behavior<PotRoomMessage> onGetPotSession(
            ActorRef<PotRoomMessage> chatRoom, EnterPot enterPot) {

        validate(enterPot);

        context.getLog().info("Participant joined {} pot", enterPot.replyTo().path().name());

        ActorRef<ParticipantMessage> participant = enterPot.replyTo();

        participants.add(participant);

        if (getNumberOfParticipants() == participants.size()) {
            context.getLog().info("All participants joined; pot is ready to start.");

            ActorRef<SessionMessage> session =
                    context.spawn(
                            Session.create(participants, totalTurns),
                            URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8));

            participant.tell(new SessionGranted(chatRoom, session.narrow()));

            // Communicate session start and share pot info with all participants
            participants.forEach(p -> p.tell(new SessionStarted(chatRoom, session, participants, totalTurns)));

            return createPotBehaviour(chatRoom);
        } else {
            // Waiting for more participants
            context.getLog().info("Waiting for {} more participant(s).", numberOfParticipants - participants.size());
            return Behaviors.same();
        }
    }

    private void validate(EnterPot enterPot) {
        // Add check session started
        if (participants.stream()
                .anyMatch(
                        s ->
                                s.path()
                                        .name()
                                        .equals(URLEncoder.encode(enterPot.replyTo().path().name(), UTF_8)))) {

            enterPot.replyTo().tell(new SessionDenied("Can only enter a pot once"));
        }
    }


    private Behavior<PotRoomMessage> createPotBehaviour(ActorRef<PotRoomMessage> chatRoom) {
        return Behaviors.receive(PotRoomMessage.class)
                .onMessage(EnterPot.class, x -> onGetPotSession(chatRoom, x))
                .build();
    }

    public static Behavior<PotRoomMessage> create(int numberOfParticipants, int totalTurns) {
        return Behaviors.setup(
                ctx -> new PotRoom(ctx, numberOfParticipants, totalTurns).createPotBehaviour(ctx.getSelf()));
    }
}

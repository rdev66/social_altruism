package dev.freireservices.social_altruism.chat.potroom;

import dev.freireservices.social_altruism.chat.GameConfig;
import dev.freireservices.social_altruism.chat.GameResult;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.EnterPot;
import dev.freireservices.social_altruism.chat.potroom.PotRoomProtocol.PotRoomMessage;
import dev.freireservices.social_altruism.chat.potroom.SessionProtocol.SessionMessage;

import java.util.ArrayList;
import java.util.List;

import static dev.freireservices.social_altruism.chat.participant.ParticipantProtocol.*;

public class PotRoom {
    private final ActorContext<PotRoomMessage> context;
    private final List<ActorRef<ParticipantMessage>> participants;
    private final GameConfig config;
    private final ActorRef<GameResult> resultRef;
    private boolean started;

    private PotRoom(
            ActorContext<PotRoomMessage> context,
            GameConfig config,
            ActorRef<GameResult> resultRef) {
        this.context = context;
        this.participants = new ArrayList<>();
        this.config = config;
        this.resultRef = resultRef;
        this.started = false;
    }

    private Behavior<PotRoomMessage> onEnterPot(EnterPot enterPot) {
        if (!validate(enterPot)) {
            return Behaviors.same();
        }

        context.getLog().info("Participant joined {} pot", enterPot.replyTo().path().name());

        participants.add(enterPot.replyTo());

        if (config.jugadores() == participants.size()) {
            context.getLog().info("All participants joined; pot is ready to start.");
            started = true;

            ActorRef<SessionMessage> session =
                    context.spawn(
                            Session.create(config, List.copyOf(participants), resultRef),
                            "session");

            participants.forEach(p -> p.tell(new SessionGranted(context.getSelf(), session.narrow())));
            participants.forEach(
                    p -> p.tell(new SessionStarted(context.getSelf(), session, List.copyOf(participants), config)));

            return Behaviors.same();
        }

        context.getLog()
                .info(
                        "Waiting for {} more participant(s).",
                        config.jugadores() - participants.size());
        return Behaviors.same();
    }

    /**
     * @return true if the participant may join
     */
    private boolean validate(EnterPot enterPot) {
        if (started || participants.size() >= config.jugadores()) {
            enterPot.replyTo().tell(new SessionDenied("Pot full"));
            return false;
        }

        if (participants.stream().anyMatch(p -> p.equals(enterPot.replyTo()))) {
            enterPot.replyTo().tell(new SessionDenied("Can only enter a pot once"));
            return false;
        }

        return true;
    }

    private Behavior<PotRoomMessage> behavior() {
        return Behaviors.receive(PotRoomMessage.class)
                .onMessage(EnterPot.class, this::onEnterPot)
                .build();
    }

    public static Behavior<PotRoomMessage> create(
            GameConfig config, ActorRef<GameResult> resultRef) {
        return Behaviors.setup(ctx -> new PotRoom(ctx, config, resultRef).behavior());
    }
}

package dev.freireservices.social_altruism.chat;

import dev.freireservices.social_altruism.chat.participant.ParticipantType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Final outcome of a finished pot session. */
public record GameResult(
        GameConfig config,
        List<PlayerOutcome> players,
        double totalWealth,
        boolean balanceOk) {

    public record PlayerOutcome(String name, ParticipantType type, double finalCoins) {}

    public Map<ParticipantType, Double> averageByType() {
        return players.stream()
                .collect(
                        Collectors.groupingBy(
                                PlayerOutcome::type,
                                Collectors.averagingDouble(PlayerOutcome::finalCoins)));
    }

    public double averageFor(ParticipantType type) {
        return players.stream()
                .filter(p -> p.type() == type)
                .mapToDouble(PlayerOutcome::finalCoins)
                .average()
                .orElse(Double.NaN);
    }

    public long countOf(ParticipantType type) {
        return players.stream().filter(p -> p.type() == type).count();
    }
}

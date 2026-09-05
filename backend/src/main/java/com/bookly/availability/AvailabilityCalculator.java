package com.bookly.availability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out which start times are genuinely free.
 *
 * <p>A pure function: inputs in, start instants out. No repository, no clock lookup, no Spring
 * context — which is what lets the logic that actually matters be tested exhaustively without a
 * database, and what keeps daylight-saving behaviour decidable in a unit test.
 *
 * <p>Where a rule is ambiguous this refuses to offer the slot. A slot wrongly withheld costs one
 * booking; a slot wrongly offered costs a customer's trust and the owner's morning.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /**
     * @param step how far apart candidate start times are. Not the same thing as the duration: a
     *             45-minute service on a 15-minute step starts at :00, :15, :30 and :45. Conflating
     *             the two produces a plausible-looking calendar that quietly loses most of the day.
     * @return every start instant at which the whole service fits inside a working window and
     *         overlaps nothing busy, sorted, without duplicates
     */
    public static List<Instant> startTimes(LocalDate date,
                                           ZoneId zone,
                                           List<WorkingWindow> windows,
                                           List<BusyInterval> busy,
                                           Duration serviceDuration,
                                           Duration step) {
        if (serviceDuration.isZero() || serviceDuration.isNegative()) {
            throw new IllegalArgumentException("Service duration must be positive");
        }
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("Step must be positive");
        }

        List<Instant> starts = new ArrayList<>();
        for (WorkingWindow window : windows) {
            Instant windowStart = resolve(date, window.start(), zone);
            Instant windowEnd = resolve(date, window.end(), zone);

            // Stepping along the instant timeline, between endpoints that were resolved in the
            // zone, is what makes daylight saving fall out of the library rather than out of
            // arithmetic. On the spring-forward date the window is genuinely one hour shorter, so
            // one hour of slots simply does not fit; on the fall-back date it is an hour longer,
            // and the extra slots are distinct instants even though they print the same local time.
            for (Instant candidate = windowStart;
                    !candidate.plus(serviceDuration).isAfter(windowEnd);
                    candidate = candidate.plus(step)) {
                Instant candidateEnd = candidate.plus(serviceDuration);
                boolean blocked = false;
                for (BusyInterval interval : busy) {
                    if (interval.overlaps(candidate, candidateEnd)) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) {
                    starts.add(candidate);
                }
            }
        }
        return starts.stream().distinct().sorted().toList();
    }

    /**
     * Turns a local wall-clock time on a date into the instant it refers to.
     *
     * <p>Two dates a year misbehave, and both are handled deliberately rather than left to a
     * default. On the spring-forward date a local time inside the skipped hour does not exist;
     * {@code atZone} would quietly shift it, so the gap's own transition supplies the first real
     * instant instead. On the fall-back date a local time occurs twice and the earlier offset is
     * taken — stated here because it is a choice, not an accident.
     */
    private static Instant resolve(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = LocalDateTime.of(date, time);
        ZoneRules rules = zone.getRules();
        if (rules.getValidOffsets(local).isEmpty()) {
            ZoneOffsetTransition gap = rules.getTransition(local);
            return gap.getInstant();
        }
        return local.atZone(zone).toInstant();
    }
}

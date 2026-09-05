package com.bookly.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turn-2 criteria 2.1 to 2.12, over the pure calculator. No Spring context, no database — spec
 * part 4: if any of these needed one, the calculator would have taken a dependency it should not
 * have.
 *
 * <p>Written from {@code docs/spec/turn-2.md} alone; the author has not read
 * {@code backend/src/main}. That matters most here. A test written by reading the calculator would
 * re-state its arithmetic, and arithmetic that agrees with itself is precisely the failure mode
 * this suite exists to rule out.
 *
 * <p><strong>The DST expectations are derived from the clock, not from the code and not from
 * memory.</strong> The transition dates, their direction and their size all come from
 * {@link ZoneRules} at runtime, so if the tzdb rules for Asia/Jerusalem change, these tests follow
 * the change instead of asserting a stale calendar. Each DST test also states its preconditions —
 * the day really is 23 or 25 hours long, the local time really does not exist — so a failure says
 * whether the engine is wrong or the world moved.
 */
class AvailabilityCalculatorTest {

    /** The zone the rest of the project uses, and one with a real DST rule. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Jerusalem");

    private static final ZoneRules RULES = ZONE.getRules();

    /** A Wednesday in June: no transition, no month-end edge, nothing special about it. */
    private static final LocalDate ORDINARY_DAY = LocalDate.of(2026, 6, 10);

    private static final int DST_YEAR = 2026;

    // ------------------------------------------------------------------ helpers

    private static List<Instant> startTimes(
            LocalDate date,
            List<WorkingWindow> windows,
            List<BusyInterval> busy,
            Duration serviceDuration,
            Duration step) {
        return AvailabilityCalculator.startTimes(date, ZONE, windows, busy, serviceDuration, step);
    }

    private static WorkingWindow window(String start, String end) {
        return new WorkingWindow(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static BusyInterval busy(LocalDate date, String start, String end) {
        return new BusyInterval(instantAt(date, start), instantAt(date, end));
    }

    /** Only ever called for local times that exist on the given date. */
    private static Instant instantAt(LocalDate date, String localTime) {
        LocalDateTime local = LocalDateTime.of(date, LocalTime.parse(localTime));
        assertThat(RULES.getValidOffsets(local))
                .as("fixture precondition: %s exists exactly once in %s", local, ZONE)
                .hasSize(1);
        return local.atZone(ZONE).toInstant();
    }

    private static List<LocalTime> localTimesOf(List<Instant> starts) {
        return starts.stream().map(i -> i.atZone(ZONE).toLocalTime()).toList();
    }

    private static List<LocalTime> times(String... values) {
        return java.util.Arrays.stream(values).map(LocalTime::parse).toList();
    }

    /** The transition of the requested kind in the requested year, read from the tzdb. */
    private static ZoneOffsetTransition transitionIn(int year, boolean gap) {
        Instant cursor = LocalDate.of(year, 1, 1).atStartOfDay(ZONE).toInstant();
        Instant endOfYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZONE).toInstant();
        for (ZoneOffsetTransition t = RULES.nextTransition(cursor);
                t != null && t.getInstant().isBefore(endOfYear);
                t = RULES.nextTransition(t.getInstant())) {
            if (t.isGap() == gap) {
                return t;
            }
        }
        throw new AssertionError("no " + (gap ? "spring-forward" : "fall-back") + " transition in "
                + ZONE + " during " + year + "; these tests need a zone that observes DST");
    }

    private static Duration lengthOfDay(LocalDate date) {
        return Duration.between(date.atStartOfDay(ZONE), date.plusDays(1).atStartOfDay(ZONE));
    }

    // ------------------------------------------------------------------- 2.1-2.9

    /** 2.1 — a service is never offered into a free window shorter than its own duration. */
    @Test
    @DisplayName("2.1 a 45-minute service is not offered into a 30-minute gap")
    void doesNotOfferAServiceIntoAGapTooShortForIt() {
        // Free before 10:00 (one hour) and after 16:30 (half an hour). Only the first fits 45m.
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "17:00")),
                List.of(busy(ORDINARY_DAY, "10:00", "16:30")),
                Duration.ofMinutes(45),
                Duration.ofMinutes(15));

        assertThat(localTimesOf(starts))
                .as("only starts whose whole 45 minutes fit in the free hour before 10:00; the "
                        + "30-minute tail after 16:30 is too short to hold the service at all")
                .containsExactlyElementsOf(times("09:00", "09:15"));
    }

    /** 2.2 — no returned slot overlaps a busy interval. */
    @Test
    @DisplayName("2.2 no returned slot overlaps a busy interval")
    void neverOverlapsABusyInterval() {
        Duration duration = Duration.ofMinutes(30);
        List<BusyInterval> busy = List.of(
                busy(ORDINARY_DAY, "10:00", "11:00"),
                busy(ORDINARY_DAY, "13:15", "14:00"),
                busy(ORDINARY_DAY, "16:00", "16:30"));

        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("08:00", "18:00")),
                busy,
                duration,
                Duration.ofMinutes(15));

        assertThat(starts).as("a ten-hour day with three interruptions still has slots").isNotEmpty();
        for (Instant start : starts) {
            Instant end = start.plus(duration);
            for (BusyInterval interval : busy) {
                boolean overlaps = start.isBefore(interval.end()) && interval.start().isBefore(end);
                assertThat(overlaps)
                        .as("slot [%s, %s) must not overlap busy [%s, %s)",
                                start.atZone(ZONE).toLocalTime(), end.atZone(ZONE).toLocalTime(),
                                interval.start().atZone(ZONE).toLocalTime(),
                                interval.end().atZone(ZONE).toLocalTime())
                        .isFalse();
            }
        }
    }

    /**
     * 2.2, the other direction — intervals are half-open, so a slot may begin exactly when a busy
     * interval ends, and may end exactly when the next one begins. Pitfall 5: getting this wrong
     * shows up as a mysteriously missing slot at the top of every hour.
     */
    @Test
    @DisplayName("2.2 a slot may start exactly when a busy interval ends")
    void mayStartExactlyWhenABusyIntervalEnds() {
        List<Instant> afterBusy = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "12:00")),
                List.of(busy(ORDINARY_DAY, "09:00", "10:00")),
                Duration.ofMinutes(60),
                Duration.ofMinutes(60));

        assertThat(localTimesOf(afterBusy))
                .as("10:00 touches the end of the busy interval without overlapping it")
                .containsExactlyElementsOf(times("10:00", "11:00"));

        List<Instant> beforeBusy = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "11:00")),
                List.of(busy(ORDINARY_DAY, "10:00", "11:00")),
                Duration.ofMinutes(60),
                Duration.ofMinutes(60));

        assertThat(localTimesOf(beforeBusy))
                .as("09:00 ends exactly when the busy interval starts, which is not an overlap")
                .containsExactlyElementsOf(times("09:00"));
    }

    /** 2.3 — no returned slot falls outside the working window. */
    @Test
    @DisplayName("2.3 every slot lies inside the working window")
    void staysInsideTheWorkingWindow() {
        Duration duration = Duration.ofMinutes(90);
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "13:00")),
                List.of(),
                duration,
                Duration.ofMinutes(30));

        Instant windowStart = instantAt(ORDINARY_DAY, "09:00");
        Instant windowEnd = instantAt(ORDINARY_DAY, "13:00");
        assertThat(starts).isNotEmpty();
        for (Instant start : starts) {
            assertThat(start).as("slot start is not before the window opens").isBeforeOrEqualTo(windowEnd);
            assertThat(start.isBefore(windowStart)).as("slot start is not before 09:00").isFalse();
            assertThat(start.plus(duration))
                    .as("the whole service fits before the window closes")
                    .isBeforeOrEqualTo(windowEnd);
        }
        assertThat(localTimesOf(starts))
                .as("11:30 is the last 90-minute service that finishes by 13:00")
                .containsExactlyElementsOf(times("09:00", "09:30", "10:00", "10:30", "11:00", "11:30"));
    }

    /** 2.4 — two windows on one day yield slots in both and none in the break between them. */
    @Test
    @DisplayName("2.4 a break between two windows yields no slots inside the break")
    void respectsABreakBetweenTwoWindows() {
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "12:00"), window("13:00", "17:00")),
                List.of(),
                Duration.ofMinutes(60),
                Duration.ofMinutes(60));

        List<LocalTime> local = localTimesOf(starts);
        assertThat(local)
                .as("both windows contribute, and the hour between them contributes nothing")
                .containsExactlyElementsOf(
                        times("09:00", "10:00", "11:00", "13:00", "14:00", "15:00", "16:00"));
        assertThat(local)
                .as("no slot begins inside the break")
                .noneMatch(t -> !t.isBefore(LocalTime.of(12, 0)) && t.isBefore(LocalTime.of(13, 0)));
    }

    /**
     * 2.5 — candidates are generated on the configured step from the start of the window, not on
     * the service duration. Pitfall 4: conflating the two produces a calendar that looks plausible
     * and quietly loses most of the day's capacity.
     */
    @Test
    @DisplayName("2.5 a 45-minute service on a 15-minute step starts at :00, :15, :30 and :45")
    void generatesStartsOnTheConfiguredStep() {
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "11:00")),
                List.of(),
                Duration.ofMinutes(45),
                Duration.ofMinutes(15));

        assertThat(localTimesOf(starts))
                .as("every quarter hour whose 45 minutes finish by 11:00 — not only 09:00, 09:45, "
                        + "10:30, which is what stepping by the duration would give")
                .containsExactlyElementsOf(
                        times("09:00", "09:15", "09:30", "09:45", "10:00", "10:15"));
    }

    /**
     * 2.7, the half this signature can decide: starts are deduplicated by instant and ordered.
     *
     * <p>The criterion's other half — that each slot names which employees can serve it — is not
     * decidable through {@code startTimes}, which returns bare instants and takes no employee. It
     * is asserted over HTTP in {@code AvailabilityIT} instead, and reported as an ambiguity.
     * Overlapping windows are the shape that exposes the defect here: a calculator that walks each
     * window independently and concatenates emits the shared hours twice.
     */
    @Test
    @DisplayName("2.7 overlapping windows union without duplicating a start instant")
    void anyEmployeeUnionsAndDeduplicates() {
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "12:00"), window("10:00", "13:00")),
                List.of(),
                Duration.ofMinutes(60),
                Duration.ofMinutes(60));

        assertThat(starts).as("no start instant appears twice").doesNotHaveDuplicates();
        assertThat(starts).as("ordered by instant").isSorted();
        assertThat(localTimesOf(starts))
                .as("the union of both windows, each hour offered once")
                .containsExactlyElementsOf(times("09:00", "10:00", "11:00", "12:00"));
    }

    /** 2.8 — a day with no working window is empty, not an error. */
    @Test
    @DisplayName("2.8 a day with no working window returns no slots rather than failing")
    void aNonWorkingDayReturnsNoSlots() {
        List<Instant> starts = startTimes(
                ORDINARY_DAY,
                List.of(),
                List.of(busy(ORDINARY_DAY, "10:00", "11:00")),
                Duration.ofMinutes(30),
                Duration.ofMinutes(15));

        assertThat(starts).as("a day off is an empty answer, not an exception").isEmpty();
    }

    // ------------------------------------------------------------------ 2.10-2.12

    /**
     * 2.10 — on the spring-forward date a window spanning the transition loses exactly the skipped
     * hour, and every returned instant is real.
     *
     * <p>The expected difference is computed from the transition's own size, so a zone whose rule
     * changed from one hour to another would still be asserted correctly.
     */
    @Test
    @DisplayName("2.10 spring forward loses exactly the skipped hour")
    void springForwardLosesExactlyTheSkippedHour() {
        ZoneOffsetTransition gap = transitionIn(DST_YEAR, true);
        LocalDate dstDay = gap.getDateTimeBefore().toLocalDate();
        LocalDate ordinaryDay = dstDay.minusWeeks(1);
        Duration skipped = gap.getDuration();
        Duration step = Duration.ofMinutes(30);

        assertThat(lengthOfDay(ordinaryDay))
                .as("precondition: the comparison day is an ordinary 24 hours")
                .isEqualTo(Duration.ofHours(24));
        assertThat(lengthOfDay(dstDay))
                .as("precondition: the transition day is short by exactly the skipped hour")
                .isEqualTo(Duration.ofHours(24).minus(skipped));
        assertThat(skipped.toMinutes() % step.toMinutes())
                .as("precondition: the skipped hour is a whole number of steps")
                .isZero();

        // 00:00-06:00 spans the 02:00 transition on both days.
        List<WorkingWindow> windows = List.of(window("00:00", "06:00"));
        List<Instant> ordinary =
                startTimes(ordinaryDay, windows, List.of(), Duration.ofMinutes(30), step);
        List<Instant> transitionDay =
                startTimes(dstDay, windows, List.of(), Duration.ofMinutes(30), step);

        long expectedFewer = skipped.dividedBy(step);
        assertThat(ordinary.size() - transitionDay.size())
                .as("slots lost on %s relative to %s: the window is %s shorter in real time, which "
                        + "at a %s step is %d slots", dstDay, ordinaryDay, skipped, step, expectedFewer)
                .isEqualTo((int) expectedFewer);

        assertThat(transitionDay).as("no instant offered twice").doesNotHaveDuplicates();
        assertThat(transitionDay).as("ordered by instant").isSorted();
        for (Instant start : transitionDay) {
            LocalDateTime local = start.atZone(ZONE).toLocalDateTime();
            assertThat(RULES.getValidOffsets(local))
                    .as("%s renders as %s, which must be a local time that exists", start, local)
                    .isNotEmpty();
        }
    }

    /** 2.11 — on the fall-back date the same window gains an hour, with no repeated instant. */
    @Test
    @DisplayName("2.11 fall back gains an hour without duplicate instants")
    void fallBackGainsAnHourWithoutDuplicateInstants() {
        ZoneOffsetTransition overlap = transitionIn(DST_YEAR, false);
        LocalDate dstDay = overlap.getDateTimeBefore().toLocalDate();
        LocalDate ordinaryDay = dstDay.minusWeeks(1);
        Duration repeated = overlap.getDuration().abs();
        Duration step = Duration.ofMinutes(30);

        assertThat(lengthOfDay(ordinaryDay))
                .as("precondition: the comparison day is an ordinary 24 hours")
                .isEqualTo(Duration.ofHours(24));
        assertThat(lengthOfDay(dstDay))
                .as("precondition: the transition day is long by exactly the repeated hour")
                .isEqualTo(Duration.ofHours(24).plus(repeated));

        List<WorkingWindow> windows = List.of(window("00:00", "06:00"));
        List<Instant> ordinary =
                startTimes(ordinaryDay, windows, List.of(), Duration.ofMinutes(30), step);
        List<Instant> transitionDay =
                startTimes(dstDay, windows, List.of(), Duration.ofMinutes(30), step);

        long expectedExtra = repeated.dividedBy(step);
        assertThat(transitionDay.size() - ordinary.size())
                .as("slots gained on %s relative to %s: the window is %s longer in real time. "
                        + "Generating by local time alone silently drops this hour of real "
                        + "availability", dstDay, ordinaryDay, repeated)
                .isEqualTo((int) expectedExtra);

        assertThat(transitionDay)
                .as("the repeated local hour must be two distinct instants, not one offered twice")
                .doesNotHaveDuplicates();
        assertThat(transitionDay).as("ordered by instant").isSorted();
    }

    /**
     * 2.12 — a window whose local start does not exist on the spring-forward date must not produce
     * a slot at that non-existent time.
     *
     * <p>Pitfall 2: {@code ZonedDateTime.of} silently moves such a time forward rather than
     * failing, which is right for a clock and wrong for a promise to a customer. The step is chosen
     * so that both readings of "generate on the step from the start of the window" — anchoring the
     * grid at the configured start and skipping what does not exist, or clipping the window to its
     * first real instant — agree on the same answer, so this test does not depend on which one the
     * implementer chose.
     */
    @Test
    @DisplayName("2.12 a window starting in the spring-forward gap offers no non-existent time")
    void doesNotOfferANonExistentLocalTime() {
        ZoneOffsetTransition gap = transitionIn(DST_YEAR, true);
        LocalDate dstDay = gap.getDateTimeBefore().toLocalDate();
        Duration step = Duration.ofMinutes(30);
        LocalTime insideTheGap = gap.getDateTimeBefore().toLocalTime().plus(step);

        assertThat(RULES.getValidOffsets(LocalDateTime.of(dstDay, insideTheGap)))
                .as("precondition: %s does not exist on %s in %s", insideTheGap, dstDay, ZONE)
                .isEmpty();
        assertThat(gap.getDuration().toMinutes() % step.toMinutes())
                .as("precondition: the gap is a whole number of steps, so both readings agree")
                .isZero();

        List<Instant> starts = startTimes(
                dstDay,
                List.of(new WorkingWindow(insideTheGap, LocalTime.of(6, 0))),
                List.of(),
                Duration.ofMinutes(30),
                step);

        assertThat(starts)
                .as("the window still has real hours in it after the gap closes")
                .isNotEmpty()
                .doesNotHaveDuplicates()
                .isSorted();
        assertThat(starts.get(0))
                .as("the first slot is the first real instant of the window — the moment the clock "
                        + "reaches %s — not %s shifted forward by the size of the gap",
                        gap.getDateTimeAfter().toLocalTime(), insideTheGap)
                .isEqualTo(gap.getInstant());
        for (Instant start : starts) {
            LocalTime local = start.atZone(ZONE).toLocalTime();
            assertThat(local)
                    .as("%s renders as %s, which must be at or after the gap closes", start, local)
                    .isAfterOrEqualTo(gap.getDateTimeAfter().toLocalTime());
        }
    }

    // ------------------------------------------------------------------ 2.29

    /**
     * 2.29 — overlapping windows and busy periods are merged before stepping, so no single request
     * can be made arbitrarily expensive by rows a caller creates.
     *
     * <p>The rows here are ones an owner could create through the ordinary API: twenty thousand
     * copies of one working window and twenty thousand overlapping blocks. Compared pairwise that
     * is on the order of 10^10 comparisons inside a single request, which no rate limit helps with
     * — the cost sits inside one request that the caller is entitled to make.
     *
     * <p>Two things are asserted, and the first matters more than the second: the answer must be
     * the same as for the one window and one block these all collapse to. A merge that changed the
     * result would be a correctness bug wearing a performance fix. The time bound is deliberately
     * far above any plausible honest runtime, so it fails only on a cost that has gone quadratic,
     * never on a slow machine.
     */
    @Test
    @DisplayName("2.29 duplicated windows and blocks are merged, not compared pairwise")
    void mergesOverlappingWindowsAndBusyPeriodsBeforeStepping() {
        int copies = 20_000;
        List<WorkingWindow> windows = new java.util.ArrayList<>();
        List<BusyInterval> busy = new java.util.ArrayList<>();
        Instant blockStart = instantAt(ORDINARY_DAY, "10:00");
        for (int i = 0; i < copies; i++) {
            windows.add(window("09:00", "17:00"));
            // Overlapping rather than identical, but with a common end, so the union is exactly
            // the hour 10:00-11:00 and the expected answer can be worked out on paper.
            busy.add(new BusyInterval(blockStart.plusSeconds(i % 60), blockStart.plusSeconds(3600)));
        }

        long startedAt = System.nanoTime();
        List<Instant> many = startTimes(
                ORDINARY_DAY, windows, busy, Duration.ofMinutes(60), Duration.ofMinutes(60));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        List<Instant> one = startTimes(
                ORDINARY_DAY,
                List.of(window("09:00", "17:00")),
                List.of(new BusyInterval(blockStart, blockStart.plusSeconds(3600))),
                Duration.ofMinutes(60),
                Duration.ofMinutes(60));

        assertThat(many)
                .as("%d copies of one window and %d overlapping blocks describe the same day as the "
                        + "one window and one block they collapse to", copies, copies)
                .isEqualTo(one);
        assertThat(localTimesOf(many))
                .as("the busy hour is excluded exactly once")
                .containsExactlyElementsOf(
                        times("09:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00"));
        assertThat(elapsed)
                .as("computing one day from %d rows took %s; pairwise comparison of these rows is "
                        + "around 10^10 operations, so this bound is only reachable by merging",
                        copies, elapsed)
                .isLessThan(Duration.ofSeconds(5));
    }
}

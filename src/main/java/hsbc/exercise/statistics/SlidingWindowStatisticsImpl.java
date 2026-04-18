package hsbc.exercise.statistics;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {

    // ── Fenêtre glissante ──────────────────────────────────────────────────────

    private final int          windowSize;
    private final Deque<Integer> window  = new ArrayDeque<>();
    private final Object         winLock = new Object();

    // ── Cache ──────────────────────────────────────────────────────────────────

    private final AtomicReference<Statistics> latestStats = new AtomicReference<>();

    // ── Subscriptions ──────────────────────────────────────────────────────────

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    // ── Compute thread ─────────────────────────────────────────────────────────

    private final ExecutorService computeExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stats-compute");
                t.setDaemon(true);
                return t;
            });

    // ══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════════════════════

    public SlidingWindowStatisticsImpl(int windowSize) {
        if (windowSize <= 0)
            throw new IllegalArgumentException("windowSize doit être > 0");
        this.windowSize = windowSize;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SlidingWindowStatistics API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Thread-safe. Délègue le calcul au computeExecutor → non bloquant.
     */
    @Override
    public void add(int measurement) {
        addToWindow(measurement);
        computeExecutor.submit(this::computeAndNotify);
    }

    /**
     * @param listener         callback à appeler
     * @param filter           critère de notification — null = toujours notifier
     * @param callbackExecutor thread sur lequel appeler le listener
     */
    @Override
    public void subscribeForStatistics(StatisticsListener listener,
                                       Predicate<Statistics> filter,
                                       Executor callbackExecutor) {
        Objects.requireNonNull(listener,         "listener ne peut pas être null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor ne peut pas être null");

        Predicate<Statistics> effectiveFilter = (filter != null) ? filter : s -> true;
        subscriptions.add(new Subscription(listener, effectiveFilter, callbackExecutor));
    }

    @Override
    public Statistics getLatestStatistics() {
        return latestStats.get();
    }

    public void shutdown() {
        computeExecutor.shutdown();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private — fenêtre
    // ══════════════════════════════════════════════════════════════════════════

    private void addToWindow(int measurement) {
        synchronized (winLock) {
            if (window.size() == windowSize) {
                window.pollFirst();
            }
            window.addLast(measurement);
        }
    }

    private List<Integer> snapshotWindow() {
        synchronized (winLock) {
            return new ArrayList<>(window);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private — calcul et notification
    // ══════════════════════════════════════════════════════════════════════════

    private void computeAndNotify() {
        List<Integer> snapshot = snapshotWindow();
        if (snapshot.isEmpty()) return;

        Statistics stats = new StatisticsImpl(snapshot);
        latestStats.set(stats);
        notifySubscribers(stats);
    }

    private void notifySubscribers(Statistics stats) {
        for (Subscription sub : subscriptions) {
            if (sub.filter.test(stats)) {
                sub.executor.execute(() -> safeNotify(sub.listener, stats));
            }
        }
    }

    private void safeNotify(StatisticsListener listener, Statistics stats) {
        try {
            listener.onStatistics(stats);
        } catch (Exception e) {
            System.err.println("Erreur listener : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Inner class — Subscription
    // ══════════════════════════════════════════════════════════════════════════

    private static class Subscription {
        final StatisticsListener    listener;
        final Predicate<Statistics> filter;
        final Executor              executor;

        Subscription(StatisticsListener listener,
                     Predicate<Statistics> filter,
                     Executor executor) {
            this.listener = listener;
            this.filter   = filter;
            this.executor = executor;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Inner class — StatisticsImpl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Immutable — calculé une fois sur un snapshot.
     * Partageable entre threads sans synchronisation.
     */
    static class StatisticsImpl implements Statistics {

        private final List<Integer> sorted;
        private final double        mean;
        private final int           mode;

        StatisticsImpl(List<Integer> snapshot) {
            this.sorted = snapshot.stream()
                    .sorted()
                    .collect(Collectors.toList());
            this.mean   = computeMean(snapshot);
            this.mode   = computeMode(snapshot);
        }

        @Override public double getMean() { return mean; }
        @Override public int    getMode() { return mode; }

        /**
         * Interpolation linéaire.
         * getPctile(0)   → min  |  getPctile(50) → médiane  |  getPctile(100) → max
         */
        @Override
        public double getPctile(int pctile) {
            if (pctile < 0 || pctile > 100)
                throw new IllegalArgumentException("pctile doit être entre 0 et 100");
            if (sorted.size() == 1)
                return sorted.get(0);

            double pos   = (pctile / 100.0) * (sorted.size() - 1);
            int    lower = (int) Math.floor(pos);
            int    upper = (int) Math.ceil(pos);
            double frac  = pos - lower;

            return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
        }

        // ── helpers ────────────────────────────────────────────────────────────

        private static double computeMean(List<Integer> data) {
            return data.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
        }

        private static int computeMode(List<Integer> data) {
            return data.stream()
                    .collect(Collectors.groupingBy(i -> i, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
        }
    }
}
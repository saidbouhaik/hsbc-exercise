package hsbc.exercise.statistics;

import java.util.concurrent.Executor;
import java.util.function.Predicate;

public interface SlidingWindowStatistics {
    void add(int measurement);

    // Le client fournit :
    // - un prédicat : "notifie-moi seulement si la moyenne dépasse 100ms"
    // - un executor : "appelle-moi sur MON thread" (thread-safety côté abonné)
    void subscribeForStatistics(StatisticsListener listener,
                                Predicate<Statistics> filter,
                                Executor callbackExecutor);

    Statistics getLatestStatistics();
}

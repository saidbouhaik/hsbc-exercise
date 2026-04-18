package hsbc.exercise.bus;

@FunctionalInterface
public interface EventFilter {
    boolean filter(Object event);
}

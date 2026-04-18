package hsbc.exercise.bus;

public interface EventBus {
    void subscribe(String eventType, EventListener listener);

    void addSubscriberForFilteredEvents(String eventType, EventListener listener, EventFilter filter);

    void publishCoalescedEvent(String eventType, Object event);

    void unsubscribe(String eventType, EventListener listener);

    void publishEvent(String eventType, Object event);

    void shutdown();

}

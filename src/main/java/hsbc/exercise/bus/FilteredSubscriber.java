package hsbc.exercise.bus;

public class FilteredSubscriber {

    private final EventListener listener;
    private final EventFilter filter;

    public FilteredSubscriber(EventListener listener, EventFilter filter) {
        this.listener = listener;
        this.filter = filter;
    }

    public EventListener getListener() {
        return listener;
    }

    public EventFilter getFilter() {
        return filter;
    }
}

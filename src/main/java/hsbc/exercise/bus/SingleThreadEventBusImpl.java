package hsbc.exercise.bus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


//Producer appelle directement la liste des subscribers pour les notifier : couplage fort (lmax ??)
public class SingleThreadEventBusImpl implements EventBus{

    // liste des subscribers par eventType
    private final Map<String, List<FilteredSubscriber>> subscribers = new ConcurrentHashMap<>();

    //ajout d'un subscriber standard (sans filtre : donc filtre = true)
    @Override
    public void subscribe(String eventType, EventListener listener) {
        addSubscriberForFilteredEvents(eventType, listener, _ -> true);
    }

    @Override
    public void addSubscriberForFilteredEvents(String eventType, EventListener listener, EventFilter filter) {
        // Ajouter un filtered listner a la liste des listners si elle existe sinon on l'a créee si elle n'existe pas
        subscribers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>())
                .add(new FilteredSubscriber(listener, filter));
    }

    @Override
    public void publishCoalescedEvent(String eventType, Object event) {
        publishEvent(eventType, event);
    }

    @Override
    public void unsubscribe(String eventType, EventListener listener) {

        if (eventType == null || listener == null) return;

        //recuperer la liste des filtered subscirber
        List<FilteredSubscriber> filteredSubscribers = subscribers.get(eventType);

        if (filteredSubscribers != null) {
            filteredSubscribers.removeIf(fs -> fs != null && fs.getListener() == listener);
        }

        // Suppression de l'event Type si la liste des filteredSubscribers est vide
        if ( Objects.requireNonNull(filteredSubscribers).isEmpty()){
            subscribers.remove(eventType);
        }
    }


    //Cette méthode sera appelée par le producer
    @Override
    public void publishEvent(String eventType, Object event) {

        if (eventType == null) return;

        //Recuperer la liste des filtered subscribers qui correspond à l'event type
        List<FilteredSubscriber> filteredSubscribers = subscribers.get(eventType);

        if (filteredSubscribers == null || filteredSubscribers.isEmpty()) return;

        // Parcourir la luste des subscribers et pour chaque subscriber on verifie si l'evenement
        // match le filtre, si oui on notifie le subscriber

        for (FilteredSubscriber filteredSubscriber : filteredSubscribers) {
            if (filteredSubscriber != null && filteredSubscriber.getFilter() != null && filteredSubscriber.getFilter().filter(event)) {
                //Notification du subscriber
                // Info: si le subscriber est lent, on est bloqué jusqu'a la fin du traitement du subscirber => mono-thread
                filteredSubscriber.getListener().OnEvent(event);
            }
        }
    }

    @Override
    public void shutdown() {
        subscribers.clear();
    }
}

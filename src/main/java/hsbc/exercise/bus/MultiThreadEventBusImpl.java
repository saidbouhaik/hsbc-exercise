package hsbc.exercise.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class MultiThreadEventBusImpl implements EventBus {

    // garantir la thread safety
    private final Map<String, List<FilteredSubscriber>> subscribers = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Une BlockingQueue par type d'événement pour isoler les flux.
    private final Map<String, BlockingQueue<Object>> eventQueues = new ConcurrentHashMap<>();



    // Map pour les threads de dispatch actifs par eventType. je créer un  future, parsque l'event n'est pas encore arrivé.
    // Pour telecommander les workers
    private final Map<String, Future<?>> dispatcherTasks = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Retourne (ou crée) la BlockingQueue + le thread de dispatch pour un eventType.
     * Appelé à chaque subscribe / publish pour garantir que la queue existe.
     */
    private BlockingQueue<Object> ensureQueueAndDispatcher(String eventType) {
        return eventQueues.computeIfAbsent(eventType, k -> {
            //1- creation de la BQ
            BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

            // 2- Creation du worker pour le TAKE/pooling (Lance un thread de dispatch dédié à ce type)
            Future<?> task = executor.submit(() -> { // submit créer un thread. l'executor t'associer un thread a ton traitement
                while (running || !queue.isEmpty()) { // thread tjrs actif: alive
                    try {
                        // Bloque jusqu'à ce qu'un événement arrive (timeout pour check running)

                        Object event;
                        synchronized (getCoalesceLock(k)) {
                            event = queue.poll(); // non bloquant à l'intérieur du lock
                        }

                        if (event == null) {
                            // Pas d'événement → on attend un peu avant de réessayer
                            Thread.sleep(1);
                            continue;
                        }

                        List<FilteredSubscriber> subs = subscribers.get(k);
                        if (subs == null || subs.isEmpty()) continue;  // ← ajout du isEmpty()

                        // Snapshot pour éviter ConcurrentModificationException
                        List<FilteredSubscriber> snapshot = new ArrayList<>(subs);
                        for (FilteredSubscriber fs : snapshot) {
                            if (fs == null || fs.getListener() == null) continue;

                            if (fs.getFilter() == null || fs.getFilter().filter(event)) {
                                // Chaque listener dans son propre thread worker
                                // pour ne pas bloque les autres listners s' il y a un Listner qui fait un traitement lent
                                executor.submit(() -> fs.getListener().OnEvent(event));
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            dispatcherTasks.put(k, task);
            return queue;
        });
    }

    // ── EventBus API ───────────────────────────────────────────────────────────

    @Override
    public void subscribe(String eventType, EventListener listener) {
        addSubscriberForFilteredEvents(eventType, listener, null);
    }

    @Override
    public void addSubscriberForFilteredEvents(String eventType,
                                               EventListener listener,
                                               EventFilter filter) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType ne peut pas être null ou vide");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener ne peut pas être null");
        }

        subscribers
                .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new FilteredSubscriber(listener, filter));

        ensureQueueAndDispatcher(eventType);
    }

    @Override
    public void publishEvent(String eventType, Object event) {
        if (!running) return;
        // push l'evt dans la BQ
        ensureQueueAndDispatcher(eventType).offer(event);
    }

    /**
     * Coalesced : si un événement du même type est déjà en attente dans la queue,
     * on ne le duplique pas — on garde seulement le plus récent.
     */
    @Override
    public void publishCoalescedEvent(String eventType, Object event) {
        if (!running) return;
        BlockingQueue<Object> queue = ensureQueueAndDispatcher(eventType);

        // Retire tous les événements déjà en attente pour ce type, puis insère le nouveau
        //queue.removeIf(e -> true);   // vide la queue (coalesce = on garde le dernier)
        //queue.offer(event);

        // synchronized garantit que removeIf + offer sont atomiques
        // → le dispatcher ne peut pas consommer entre les deux opérations
        synchronized (getCoalesceLock(eventType)) {
            queue.clear();
            queue.offer(event);
        }
    }

    private final ConcurrentHashMap<String, Object> coalesceLocks = new ConcurrentHashMap<>();

    private Object getCoalesceLock(String eventType) {
        return coalesceLocks.computeIfAbsent(eventType, k -> new Object());
    }

    @Override
    public void unsubscribe(String eventType, EventListener listener) {
        if (eventType == null || listener == null) return; // ← au lieu de crasher

        List<FilteredSubscriber> subs = subscribers.get(eventType);
        if (subs != null) {
            subs.removeIf(fs -> fs != null && fs.getListener() == listener);
        }
    }

    @Override
    public void shutdown() {
        running = false;

        // 1. Interrompt chaque dispatcher individuellement → sortie immédiate du sleep
        dispatcherTasks.values().forEach(task -> task.cancel(true));

        // 2. Arrêt propre de l'executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
 }
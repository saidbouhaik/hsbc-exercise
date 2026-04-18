package hsbc.exercise.bus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MultiThreadEventBusImplTest {

    private MultiThreadEventBusImpl eventBus;

    @Mock
    private EventListener listenerA;

    @Mock
    private EventListener listenerB;

    @BeforeEach
    void setUp() {
        eventBus = new MultiThreadEventBusImpl();
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    // ── subscribe / publishEvent ───────────────────────────────────────────────

    @Test
    @DisplayName("Un listener reçoit l'événement publié sur son type")
    void testSubscribeAndReceiveEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // On utilise un vrai listener (pas un mock) pour pouvoir utiliser le latch
        EventListener listener = event -> latch.countDown();

        eventBus.subscribe("PRICE", listener);
        eventBus.publishEvent("PRICE", "100.0");

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Le listener aurait dû recevoir l'événement");
    }

    @Test
    @DisplayName("Un listener ne reçoit pas les événements d'un autre type")
    void testListenerDoesNotReceiveOtherEventType() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        EventListener listener = event -> latch.countDown();

        eventBus.subscribe("PRICE", listener);
        eventBus.publishEvent("ORDER", "BUY 100"); // mauvais type

        assertFalse(latch.await(500, TimeUnit.MILLISECONDS),
                "Le listener ne devrait pas recevoir un événement d'un autre type");
    }

    @Test
    @DisplayName("Plusieurs listeners reçoivent tous le même événement")
    void testMultipleListenersReceiveSameEvent() throws InterruptedException {
        int count = 3;
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            eventBus.subscribe("TRADE", event -> latch.countDown());
        }

        eventBus.publishEvent("TRADE", "SELL 50 AAPL");

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Tous les listeners auraient dû recevoir l'événement");
    }

    @Test
    @DisplayName("Un listener reçoit plusieurs événements successifs")
    void testListenerReceivesMultipleEvents() throws InterruptedException {
        int eventCount = 5;
        CountDownLatch latch = new CountDownLatch(eventCount);

        eventBus.subscribe("TICK", event -> latch.countDown());

        for (int i = 0; i < eventCount; i++) {
            eventBus.publishEvent("TICK", "event_" + i);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Le listener aurait dû recevoir tous les événements");
    }

    // ── addSubscriberForFilteredEvents ─────────────────────────────────────────

    @Test
    @DisplayName("Filtre actif : listener reçoit uniquement les événements qui passent")
    void testFilteredSubscriberReceivesOnlyMatchingEvents() throws InterruptedException {
        CountDownLatch latch        = new CountDownLatch(1);
        AtomicInteger  callCount    = new AtomicInteger(0);
        AtomicReference<Object> received = new AtomicReference<>();

        EventFilter buyFilter = event -> event instanceof String s && s.startsWith("BUY");

        eventBus.addSubscriberForFilteredEvents("ORDER", event -> {
            callCount.incrementAndGet();
            received.set(event);
            latch.countDown();
        }, buyFilter);

        eventBus.publishEvent("ORDER", "SELL 100 MSFT"); // filtré
        eventBus.publishEvent("ORDER", "BUY 50 GOOG");   // passe

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, callCount.get(), "Le filtre devrait bloquer SELL");
        assertEquals("BUY 50 GOOG", received.get());
    }

    @Test
    @DisplayName("Filtre null : équivalent à subscribe — reçoit tout")
    void testNullFilterReceivesAllEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        eventBus.addSubscriberForFilteredEvents("QUOTE", event -> latch.countDown(), null);

        eventBus.publishEvent("QUOTE", "event1");
        eventBus.publishEvent("QUOTE", "event2");

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Listener filtré et listener standard coexistent sur le même type")
    void testFilteredAndStandardListenerCoexist() throws InterruptedException {
        CountDownLatch latchA = new CountDownLatch(2); // reçoit tout
        CountDownLatch latchB = new CountDownLatch(1); // reçoit seulement > 100

        AtomicInteger countB = new AtomicInteger(0);

        eventBus.subscribe("DATA", event -> latchA.countDown());
        eventBus.addSubscriberForFilteredEvents("DATA",
                event -> {
                    countB.incrementAndGet();
                    latchB.countDown();
                },
                event -> event instanceof Integer i && i > 100);

        eventBus.publishEvent("DATA", 50);  // listenerA oui, listenerB non
        eventBus.publishEvent("DATA", 200); // les deux

        assertTrue(latchA.await(2, TimeUnit.SECONDS), "listenerA devrait recevoir 2 events");
        assertTrue(latchB.await(2, TimeUnit.SECONDS), "listenerB devrait recevoir 1 event");
        assertEquals(1, countB.get(), "listenerB ne devrait recevoir que l'event > 100");
    }

    // ── unsubscribe ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Un listener désinscrit ne reçoit plus d'événements")
    void testUnsubscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        EventListener listener = event -> latch.countDown();

        eventBus.subscribe("PRICE", listener);
        eventBus.unsubscribe("PRICE", listener);

        eventBus.publishEvent("PRICE", "99.0");

        assertFalse(latch.await(500, TimeUnit.MILLISECONDS),
                "Le listener désinscrit ne devrait plus recevoir d'événements");
    }

    @Test
    @DisplayName("Seul le listener désinscrit est retiré, les autres restent actifs")
    void testUnsubscribeOnlyRemovesTargetListener() throws InterruptedException {
        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        EventListener listenerA = event -> latchA.countDown();
        EventListener listenerB = event -> latchB.countDown();

        eventBus.subscribe("PRICE", listenerA);
        eventBus.subscribe("PRICE", listenerB);

        eventBus.unsubscribe("PRICE", listenerA);
        eventBus.publishEvent("PRICE", "88.0");

        assertFalse(latchA.await(500, TimeUnit.MILLISECONDS),
                "listenerA désinscrit ne devrait pas recevoir l'événement");
        assertTrue(latchB.await(2, TimeUnit.SECONDS),
                "listenerB devrait toujours recevoir l'événement");
    }

    @Test
    @DisplayName("unsubscribe sur un type inexistant ne lève pas d'exception")
    void testUnsubscribeUnknownType() {
        assertDoesNotThrow(() -> eventBus.unsubscribe("UNKNOWN", listenerA));
    }

    // ── publishCoalescedEvent ──────────────────────────────────────────────────

    @Test
    @DisplayName("publishCoalescedEvent : seul le dernier événement est traité")
    void testCoalescedEventKeepsOnlyLast() throws InterruptedException {
        // Bloque le dispatcher le temps d'injecter plusieurs événements coalescés
        CountDownLatch blockLatch   = new CountDownLatch(1);
        CountDownLatch receiveLatch = new CountDownLatch(1);
        AtomicReference<Object> lastReceived = new AtomicReference<>();

        // Premier subscribe : bloque le dispatcher
        eventBus.subscribe("QUOTE", event -> {
            try { blockLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Deuxième subscriber : capture la valeur reçue
        eventBus.addSubscriberForFilteredEvents("QUOTE", event -> {
            lastReceived.set(event);
            receiveLatch.countDown();
        }, null);

        // Flood coalescé — seul le dernier doit arriver
        for (int i = 1; i <= 10; i++) {
            eventBus.publishCoalescedEvent("QUOTE", "price_" + i);
        }

        blockLatch.countDown(); // débloque le dispatcher

        assertTrue(receiveLatch.await(2, TimeUnit.SECONDS));
        assertEquals("price_10", lastReceived.get(),
                "Seul le dernier événement coalescé devrait être reçu");
    }

    // ── shutdown ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishEvent après shutdown n'envoie pas d'événement")
    void testPublishAfterShutdownIsIgnored() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        eventBus.subscribe("NEWS", event -> latch.countDown());

        eventBus.shutdown();
        eventBus.publishEvent("NEWS", "late event");

        assertFalse(latch.await(500, TimeUnit.MILLISECONDS),
                "Aucun événement ne devrait être publié après shutdown");
    }

    @Test
    @DisplayName("shutdown est idempotent — double appel sans exception")
    void testDoubleShutdown() {
        assertDoesNotThrow(() -> {
            eventBus.shutdown();
            eventBus.shutdown();
        });
    }

    // ── concurrence ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Publishers concurrents : tous les événements sont reçus")
    void testConcurrentPublishers() throws InterruptedException {
        int threadCount = 10;
        int eventsEach  = 50;
        int total       = threadCount * eventsEach;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch received  = new CountDownLatch(total);

        eventBus.subscribe("MARKET", event -> received.countDown());

        ExecutorService publishers = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            publishers.submit(() -> {
                try {
                    startGate.await(); // tous partent en même temps
                    for (int i = 0; i < eventsEach; i++) {
                        eventBus.publishEvent("MARKET", "tick_" + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startGate.countDown(); // top départ

        assertTrue(received.await(5, TimeUnit.SECONDS),
                "Tous les " + total + " événements auraient dû être reçus");

        publishers.shutdownNow();
    }

    @Test
    @DisplayName("Subscribe/unsubscribe concurrent ne lève pas d'exception")
    void testConcurrentSubscribeUnsubscribe() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch  latch         = new CountDownLatch(threadCount);
        AtomicBoolean   errorDetected = new AtomicBoolean(false);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    EventListener l = event -> {};
                    eventBus.subscribe("STREAM", l);
                    eventBus.publishEvent("STREAM", "event_" + idx);
                    eventBus.unsubscribe("STREAM", l);
                } catch (Exception e) {
                    errorDetected.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertFalse(errorDetected.get(),
                "Aucune exception ne doit être levée en contexte concurrent");
    }

    @Test
    @DisplayName("Deux types d'événements indépendants ne s'interfèrent pas")
    void testTwoEventTypesAreIndependent() throws InterruptedException {
        CountDownLatch latchPrice = new CountDownLatch(3);
        CountDownLatch latchOrder = new CountDownLatch(2);

        eventBus.subscribe("PRICE", event -> latchPrice.countDown());
        eventBus.subscribe("ORDER", event -> latchOrder.countDown());

        eventBus.publishEvent("PRICE", "p1");
        eventBus.publishEvent("ORDER", "o1");
        eventBus.publishEvent("PRICE", "p2");
        eventBus.publishEvent("PRICE", "p3");
        eventBus.publishEvent("ORDER", "o2");

        assertTrue(latchPrice.await(2, TimeUnit.SECONDS), "PRICE : 3 events attendus");
        assertTrue(latchOrder.await(2, TimeUnit.SECONDS), "ORDER : 2 events attendus");
    }
}
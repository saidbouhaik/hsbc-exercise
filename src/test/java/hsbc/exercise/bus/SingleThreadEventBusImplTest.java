package hsbc.exercise.bus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleThreadEventBusImplTest {

    private SingleThreadEventBusImpl eventBus;

    @Mock
    private EventListener listenerA;

    @Mock
    private EventListener listenerB;

    @BeforeEach
    void setUp() {
        eventBus = new SingleThreadEventBusImpl();
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    // ── subscribe / publishEvent ───────────────────────────────────────────────

    @Test
    @DisplayName("Un listener reçoit l'événement publié sur son type")
    void testSubscribeAndPublish() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.publishEvent("PRICE", "100.0");

        // vérifie que OnEvent a été appelé exactement 1 fois avec "100.0"
        verify(listenerA, times(1)).OnEvent("100.0");
    }

    @Test
    @DisplayName("Un listener ne reçoit pas les événements d'un autre type")
    void testListenerDoesNotReceiveOtherEventType() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.publishEvent("ORDER", "BUY 100");

        verify(listenerA, never()).OnEvent(any());
    }

    @Test
    @DisplayName("Plusieurs listeners reçoivent tous le même événement")
    void testMultipleListenersReceiveSameEvent() {
        eventBus.subscribe("TRADE", listenerA);
        eventBus.subscribe("TRADE", listenerB);

        eventBus.publishEvent("TRADE", "SELL 50 AAPL");

        verify(listenerA, times(1)).OnEvent("SELL 50 AAPL");
        verify(listenerB, times(1)).OnEvent("SELL 50 AAPL");
    }

    @Test
    @DisplayName("publishEvent sans subscriber ne lève pas d'exception")
    void testPublishWithNoSubscriber() {
        assertDoesNotThrow(() -> eventBus.publishEvent("UNKNOWN", "data"));
    }

    @Test
    @DisplayName("Plusieurs événements publiés → listener appelé autant de fois")
    void testListenerCalledForEachPublish() {
        eventBus.subscribe("TICK", listenerA);

        eventBus.publishEvent("TICK", "event1");
        eventBus.publishEvent("TICK", "event2");
        eventBus.publishEvent("TICK", "event3");

        verify(listenerA, times(3)).OnEvent(any());
    }

    // ── addSubscriberForFilteredEvents ─────────────────────────────────────────

    @Test
    @DisplayName("Filtre actif : listener reçoit uniquement les événements qui passent")
    void testFilteredSubscriberReceivesOnlyMatchingEvents() {
        // Filtre : accepte uniquement les String commençant par "BUY"
        EventFilter buyFilter = event -> event instanceof String s && s.startsWith("BUY");

        eventBus.addSubscriberForFilteredEvents("ORDER", listenerA, buyFilter);

        eventBus.publishEvent("ORDER", "SELL 100 MSFT"); // filtré → pas de callback
        eventBus.publishEvent("ORDER", "BUY 50 GOOG");   // passe  → callback

        verify(listenerA, times(1)).OnEvent("BUY 50 GOOG");
        verify(listenerA, never()).OnEvent("SELL 100 MSFT");
    }

    @Test
    @DisplayName("Filtre qui rejette tout : listener jamais appelé")
    void testFilterRejectsAll() {
        EventFilter rejectAll = event -> false;

        eventBus.addSubscriberForFilteredEvents("QUOTE", listenerA, rejectAll);
        eventBus.publishEvent("QUOTE", "data");

        verify(listenerA, never()).OnEvent(any());
    }

    @Test
    @DisplayName("Filtre qui accepte tout : équivalent à subscribe normal")
    void testFilterAcceptsAll() {
        EventFilter acceptAll = event -> true;

        eventBus.addSubscriberForFilteredEvents("QUOTE", listenerA, acceptAll);
        eventBus.publishEvent("QUOTE", "data");

        verify(listenerA, times(1)).OnEvent("data");
    }

    @Test
    @DisplayName("Listener filtré et listener standard coexistent sur le même type")
    void testFilteredAndStandardListenerOnSameType() {
        EventFilter numberFilter = event -> event instanceof Integer i && i > 100;

        eventBus.subscribe("DATA", listenerA);                                    // reçoit tout
        eventBus.addSubscriberForFilteredEvents("DATA", listenerB, numberFilter); // reçoit si > 100

        eventBus.publishEvent("DATA", 50);  // listenerA oui, listenerB non
        eventBus.publishEvent("DATA", 200); // les deux

        verify(listenerA, times(2)).OnEvent(any());
        verify(listenerB, times(1)).OnEvent(200);
        verify(listenerB, never()).OnEvent(50);
    }

    // ── unsubscribe ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Un listener désinscrit ne reçoit plus d'événements")
    void testUnsubscribe() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.unsubscribe("PRICE", listenerA);

        eventBus.publishEvent("PRICE", "99.0");

        verify(listenerA, never()).OnEvent(any());
    }

    @Test
    @DisplayName("Seul le listener désinscrit est retiré, les autres restent actifs")
    void testUnsubscribeOnlyRemovesTargetListener() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.subscribe("PRICE", listenerB);

        eventBus.unsubscribe("PRICE", listenerA);
        eventBus.publishEvent("PRICE", "88.0");

        verify(listenerA, never()).OnEvent(any());
        verify(listenerB, times(1)).OnEvent("88.0");
    }

    @Test
    @DisplayName("unsubscribe supprime le type de la map si la liste devient vide")
    void testUnsubscribeRemovesEventTypeWhenListEmpty() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.unsubscribe("PRICE", listenerA);

        // Re-publish : ne doit pas lever NullPointerException
        assertDoesNotThrow(() -> eventBus.publishEvent("PRICE", "data"));
        verify(listenerA, never()).OnEvent(any());
    }

    // ── shutdown ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Après shutdown, publishEvent ne notifie plus personne")
    void testShutdownClearsSubscribers() {
        eventBus.subscribe("PRICE", listenerA);
        eventBus.shutdown();

        eventBus.publishEvent("PRICE", "data");

        verify(listenerA, never()).OnEvent(any());
    }

    @Test
    @DisplayName("shutdown est idempotent (double appel sans exception)")
    void testDoubleShutdown() {
        eventBus.subscribe("PRICE", listenerA);
        assertDoesNotThrow(() -> {
            eventBus.shutdown();
            eventBus.shutdown();
        });
    }
}
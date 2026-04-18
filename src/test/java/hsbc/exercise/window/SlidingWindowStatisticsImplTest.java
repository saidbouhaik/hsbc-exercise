package hsbc.exercise.statistics;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowStatisticsImplTest {

    private SlidingWindowStatisticsImpl stats;
    private ExecutorService             callbackExecutor;

    @BeforeEach
    void setUp() {
        stats            = new SlidingWindowStatisticsImpl(5);
        callbackExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        stats.shutdown();
        callbackExecutor.shutdownNow();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // StatisticsImpl — getMean()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMean() retourne la moyenne exacte")
    void testGetMean() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(10, 20, 30));
        assertEquals(20.0, s.getMean(), 1e-9);
    }

    @Test
    @DisplayName("getMean() sur une seule valeur retourne cette valeur")
    void testGetMeanSingleValue() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(42));
        assertEquals(42.0, s.getMean(), 1e-9);
    }

    @Test
    @DisplayName("getMean() avec valeurs négatives")
    void testGetMeanNegativeValues() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(-10, 0, 10));
        assertEquals(0.0, s.getMean(), 1e-9);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // StatisticsImpl — getMode()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMode() retourne la valeur la plus fréquente")
    void testGetMode() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(1, 2, 2, 3, 2));
        assertEquals(2, s.getMode());
    }

    @Test
    @DisplayName("getMode() sur une seule valeur retourne cette valeur")
    void testGetModeSingleValue() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(7));
        assertEquals(7, s.getMode());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // StatisticsImpl — getPctile()
    // ══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "getPctile({0}) = {1}")
    @CsvSource({
            "0,   10.0",   // minimum
            "50,  30.0",   // médiane
            "100, 50.0"    // maximum
    })
    @DisplayName("getPctile() retourne la bonne valeur pour p0 / p50 / p100")
    void testGetPctileStandardValues(int pctile, double expected) {
        // [10, 20, 30, 40, 50]
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(
                List.of(10, 20, 30, 40, 50));
        assertEquals(expected, s.getPctile(pctile), 1e-9);
    }

    @Test
    @DisplayName("getPctile() interpolation linéaire entre deux valeurs")
    void testGetPctileInterpolation() {
        // [0, 100] → p50 = 50.0 par interpolation
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(0, 100));
        assertEquals(50.0, s.getPctile(50), 1e-9);
    }

    @Test
    @DisplayName("getPctile() valeur invalide lève IllegalArgumentException")
    void testGetPctileInvalidThrows() {
        var s = new SlidingWindowStatisticsImpl.StatisticsImpl(List.of(10, 20));
        assertThrows(IllegalArgumentException.class, () -> s.getPctile(-1));
        assertThrows(IllegalArgumentException.class, () -> s.getPctile(101));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fenêtre glissante
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("windowSize <= 0 lève IllegalArgumentException")
    void testInvalidWindowSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowStatisticsImpl(0));
        assertThrows(IllegalArgumentException.class,
                () -> new SlidingWindowStatisticsImpl(-5));
    }

    @Test
    @DisplayName("getLatestStatistics() retourne null avant tout add()")
    void testLatestStatsNullBeforeAdd() {
        assertNull(stats.getLatestStatistics());
    }

    @Test
    @DisplayName("La fenêtre évince la valeur la plus ancienne quand elle est pleine")
    void testSlidingWindowEvictsOldest() throws InterruptedException {
        // windowSize = 5 → après 6 add(), la première valeur (100) est évincée
        // fenêtre finale = [10, 20, 30, 40, 50] → moyenne = 30.0
        CountDownLatch latch = new CountDownLatch(6);
        stats.subscribeForStatistics(s -> latch.countDown(), s -> true, callbackExecutor);

        stats.add(100); // sera évincé
        stats.add(10);
        stats.add(20);
        stats.add(30);
        stats.add(40);
        stats.add(50);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(30.0, stats.getLatestStatistics().getMean(), 1e-9);
    }

    @Test
    @DisplayName("getLatestStatistics() retourne les stats correctes après add()")
    void testLatestStatsAfterAdd() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        stats.subscribeForStatistics(s -> latch.countDown(), s -> true, callbackExecutor);

        stats.add(10);
        stats.add(20);
        stats.add(30);

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        Statistics latest = stats.getLatestStatistics();
        assertNotNull(latest);
        assertEquals(20.0,  latest.getMean(),       1e-9);
        assertEquals(10.0,  latest.getPctile(0),    1e-9);
        assertEquals(30.0,  latest.getPctile(100),  1e-9);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // subscribeForStatistics
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("listener null lève NullPointerException")
    void testNullListenerThrows() {
        assertThrows(NullPointerException.class,
                () -> stats.subscribeForStatistics(null, s -> true, callbackExecutor));
    }

    @Test
    @DisplayName("callbackExecutor null lève NullPointerException")
    void testNullExecutorThrows() {
        assertThrows(NullPointerException.class,
                () -> stats.subscribeForStatistics(s -> {}, s -> true, null));
    }

    @Test
    @DisplayName("Le listener est notifié à chaque add()")
    void testListenerNotifiedOnEachAdd() throws InterruptedException {
        int addCount = 4;
        CountDownLatch latch = new CountDownLatch(addCount);
        stats.subscribeForStatistics(s -> latch.countDown(), s -> true, callbackExecutor);

        for (int i = 0; i < addCount; i++) stats.add(i * 10);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Filtre async : listener appelé quand la moyenne dépasse le seuil")
    void testFilterAsyncNotification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        stats.subscribeForStatistics(
                s -> latch.countDown(),
                s -> s.getMean() > 50.0,
                callbackExecutor
        );

        stats.add(10);
        stats.add(20);
        stats.add(100);
        stats.add(100); // moyenne 57.5 → doit déclencher la notification

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Le listener aurait dû être notifié quand la moyenne dépasse 50");
    }

    @Test
    @DisplayName("Filtre null : listener toujours notifié")
    void testNullFilterAlwaysNotifies() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        stats.subscribeForStatistics(s -> latch.countDown(), null, callbackExecutor);

        stats.add(1);
        stats.add(2);
        stats.add(3);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Plusieurs abonnés sont tous notifiés")
    void testMultipleSubscribersAllNotified() throws InterruptedException {
        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        ExecutorService executorB = Executors.newSingleThreadExecutor();

        stats.subscribeForStatistics(s -> latchA.countDown(), s -> true, callbackExecutor);
        stats.subscribeForStatistics(s -> latchB.countDown(), s -> true, executorB);

        stats.add(42);

        assertTrue(latchA.await(2, TimeUnit.SECONDS), "abonné A devrait être notifié");
        assertTrue(latchB.await(2, TimeUnit.SECONDS), "abonné B devrait être notifié");

        executorB.shutdownNow();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Thread safety
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("add() concurrent depuis plusieurs threads — aucune exception")
    void testConcurrentAdd() throws InterruptedException {
        int threadCount   = 10;
        int addsPerThread = 50;
        int total         = threadCount * addsPerThread;

        stats = new SlidingWindowStatisticsImpl(total);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch received  = new CountDownLatch(total);
        stats.subscribeForStatistics(s -> received.countDown(), s -> true, callbackExecutor);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < addsPerThread; i++) stats.add(i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startGate.countDown();
        assertTrue(received.await(5, TimeUnit.SECONDS),
                "Tous les " + total + " calculs auraient dû être effectués");
        pool.shutdownNow();
    }

    @Test
    @DisplayName("Le callback s'exécute sur le thread de l'abonné, pas celui de add()")
    void testCallbackOnSubscriberThread() throws InterruptedException {
        CountDownLatch             latch      = new CountDownLatch(1);
        AtomicReference<String>    threadName = new AtomicReference<>();

        ExecutorService dedicated = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "subscriber-thread"));

        stats.subscribeForStatistics(
                s -> { threadName.set(Thread.currentThread().getName()); latch.countDown(); },
                s -> true,
                dedicated
        );

        stats.add(42);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("subscriber-thread", threadName.get(),
                "Le callback doit s'exécuter sur le thread fourni par l'abonné");

        dedicated.shutdownNow();
    }
}
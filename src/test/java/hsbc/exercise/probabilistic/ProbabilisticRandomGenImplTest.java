package hsbc.exercise.probabilistic;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProbabilisticRandomGenImplTest {

    // Distribution de base utilisée dans la majorité des tests
    private static final List<NumAndProbability> BASE_SAMPLE = List.of(
            new NumAndProbability(3, 0.20f),
            new NumAndProbability(7, 0.30f),
            new NumAndProbability(9, 0.50f)
    );

    // ══════════════════════════════════════════════════════════════════════════
    // Factory Method — of()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("of() retourne une instance non nulle avec une liste valide")
    void testOfReturnsInstance() {
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(BASE_SAMPLE);
        assertNotNull(gen);
    }

    @Test
    @DisplayName("of() avec seed retourne une instance non nulle")
    void testOfWithSeedReturnsInstance() {
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(BASE_SAMPLE, new Random(42));
        assertNotNull(gen);
    }

    @Test
    @DisplayName("of() avec une liste à un seul élément (probabilité = 1.0)")
    void testOfSingleElement() {
        List<NumAndProbability> single = List.of(new NumAndProbability(42, 1.0f));
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(single);
        assertEquals(42, gen.nextFromSample());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Validation — liste invalide
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("of() lève IllegalArgumentException si la liste est null")
    void testNullSampleThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProbabilisticRandomGenImpl.of(null)
        );
        assertEquals("La liste ne peut pas être vide.", ex.getMessage());
    }

    @Test
    @DisplayName("of() lève IllegalArgumentException si la liste est vide")
    void testEmptySampleThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProbabilisticRandomGenImpl.of(List.of())
        );
        assertEquals("La liste ne peut pas être vide.", ex.getMessage());
    }

    @Test
    @DisplayName("of() lève IllegalArgumentException si somme des probabilités != 1.0")
    void testInvalidSumThrows() {
        List<NumAndProbability> invalid = List.of(
                new NumAndProbability(1, 0.30f),
                new NumAndProbability(2, 0.30f)  // somme = 0.60
        );
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProbabilisticRandomGenImpl.of(invalid)
        );
        assertTrue(ex.getMessage().contains("La somme des probabilités doit être 1.0"));
    }

    @Test
    @DisplayName("of() lève IllegalArgumentException si une probabilité est négative")
    void testNegativeProbabilityThrows() {
        List<NumAndProbability> invalid = List.of(
                new NumAndProbability(1,  1.20f),
                new NumAndProbability(2, -0.20f)
        );
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProbabilisticRandomGenImpl.of(invalid)
        );
        assertTrue(ex.getMessage().contains("Probabilité négative interdite"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // nextFromSample() — résultats déterministes avec seed fixe
    // ══════════════════════════════════════════════════════════════════════════

   @Test
    @DisplayName("nextFromSample() avec seed 1 — séquence déterministe vérifiée")
    void testNextFromSampleDeterministicSeed1() {
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(
                BASE_SAMPLE, new Random(1));

        // Valeurs réelles de Random(1).nextFloat() :
        // 0.7309 → > 0.50           → 9
        // 0.1005 → <= 0.20          → 3
        // 0.4101 → > 0.20, <= 0.50  → 7
        // 0.4074 → > 0.20, <= 0.50  → 7
        // 0.2077 → > 0.20, <= 0.50  → 7
        assertAll(
                () -> assertEquals(9, gen.nextFromSample()), // rand=0.7309
                () -> assertEquals(3, gen.nextFromSample()), // rand=0.1005
                () -> assertEquals(7, gen.nextFromSample()), // rand=0.4101
                () -> assertEquals(7, gen.nextFromSample()), // rand=0.4074
                () -> assertEquals(7, gen.nextFromSample())  // rand=0.2077
        );
    }
    @Test
    @DisplayName("nextFromSample() retourne toujours une valeur de la distribution")
    void testNextFromSampleReturnsValidValue() {
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(BASE_SAMPLE);
        Set<Integer> validValues = Set.of(3, 7, 9);

        for (int i = 0; i < 1000; i++) {
            int result = gen.nextFromSample();
            assertTrue(validValues.contains(result),
                    "Valeur inattendue : " + result);
        }
    }

    @Test
    @DisplayName("nextFromSample() avec élément unique retourne toujours cet élément")
    void testNextFromSampleSingleElement() {
        List<NumAndProbability> single = List.of(new NumAndProbability(99, 1.0f));
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(single);

        for (int i = 0; i < 100; i++) {
            assertEquals(99, gen.nextFromSample());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // nextFromSample() — seuils limites
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Test des seuils exacts avec un Random mocké via une sous-classe.
     *
     * cumuls = [0.20, 0.50, 1.00]
     * nombres = [3, 7, 9]
     */
    @ParameterizedTest(name = "rand={0} → attendu={1}")
    @CsvSource({
            "0.00, 3",   // rand=0.00 → <= 0.20 → 3
            "0.19, 3",   // rand=0.19 → <= 0.20 → 3
            "0.20, 3",   // rand=0.20 → <= 0.20 → 3  (seuil exact)
            "0.21, 7",   // rand=0.21 → > 0.20, <= 0.50 → 7
            "0.35, 7",   // rand=0.35 → <= 0.50 → 7
            "0.50, 7",   // rand=0.50 → <= 0.50 → 7  (seuil exact)
            "0.51, 9",   // rand=0.51 → > 0.50 → 9
            "0.99, 9"    // rand=0.99 → <= 1.00 → 9
    })
    @DisplayName("nextFromSample() respecte les seuils cumulatifs")
    void testNextFromSampleBoundaries(float rand, int expected) {
        // Random contrôlé — retourne toujours la valeur spécifiée
        Random controlledRandom = new Random() {
            @Override
            public float nextFloat() { return rand; }
        };

        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(
                BASE_SAMPLE, controlledRandom);

        assertEquals(expected, gen.nextFromSample(),
                "Pour rand=" + rand + " on attendait " + expected);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // nextFromSample() — vérification statistique sur N tirages
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Distribution statistique cohérente sur 10 000 tirages")
    void testDistributionOnLargeSample() {
        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(BASE_SAMPLE);

        Map<Integer, Integer> freq = new HashMap<>();
        int N = 10_000;

        for (int i = 0; i < N; i++) {
            int val = gen.nextFromSample();
            freq.merge(val, 1, Integer::sum);
        }

        double freq3 = freq.getOrDefault(3, 0) * 100.0 / N;
        double freq7 = freq.getOrDefault(7, 0) * 100.0 / N;
        double freq9 = freq.getOrDefault(9, 0) * 100.0 / N;

        // Tolérance de ±3% sur 10 000 tirages
        assertEquals(20.0, freq3, 3.0, "Fréquence de 3 hors tolérance");
        assertEquals(30.0, freq7, 3.0, "Fréquence de 7 hors tolérance");
        assertEquals(50.0, freq9, 3.0, "Fréquence de 9 hors tolérance");
    }

    @Test
    @DisplayName("Distribution avec probabilité dominante (90%) respectée")
    void testDominantProbability() {
        List<NumAndProbability> skewed = List.of(
                new NumAndProbability(1, 0.05f),
                new NumAndProbability(2, 0.05f),
                new NumAndProbability(3, 0.90f)  // 90% des tirages
        );

        ProbabilisticRandomGenImpl gen = ProbabilisticRandomGenImpl.of(skewed);

        int count3 = 0;
        int N = 10_000;
        for (int i = 0; i < N; i++) {
            if (gen.nextFromSample() == 3) count3++;
        }

        double freq = count3 * 100.0 / N;
        assertEquals(90.0, freq, 3.0, "La valeur dominante devrait être tirée ~90% du temps");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tolérance arrondi flottant
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("of() accepte une somme à 1.0 avec légère erreur d'arrondi flottant")
    void testFloatRoundingTolerance() {
        // 0.1f + 0.2f + 0.7f ≠ exactement 1.0f en flottant
        List<NumAndProbability> sample = List.of(
                new NumAndProbability(1, 0.1f),
                new NumAndProbability(2, 0.2f),
                new NumAndProbability(3, 0.7f)
        );
        // Ne doit pas lever d'exception
        assertDoesNotThrow(() -> ProbabilisticRandomGenImpl.of(sample));
    }
}
package hsbc.exercise.probabilistic;

import java.util.List;
import java.util.Random;

public class ProbabilisticRandomGenImpl implements IProbabilisticRandomGen {

    private final int[]   numbers;
    private final float[] cumulativeProbabilities;
    private final Random  random;

    // Constructeur privé : on passe uniquement par la Factory Method
    private ProbabilisticRandomGenImpl(int[] numbers,
                                       float[] cumulativeProbabilities,
                                       Random random) {
        this.numbers               = numbers;
        this.cumulativeProbabilities = cumulativeProbabilities;
        this.random                = random;
    }

    /**
     * Factory Method — point d'entrée unique.
     * Valide, construit les tableaux cumulatifs, retourne une instance immutable.
     */
    public static ProbabilisticRandomGenImpl of(List<NumAndProbability> sample) {
        return of(sample, new Random());
    }

    /**
     * Surcharge avec seed — utile pour les tests déterministes.
     */
    public static ProbabilisticRandomGenImpl of(List<NumAndProbability> sample, Random random) {
        validate(sample);

        int     n       = sample.size();
        int[]   numbers = new int[n];
        float[] cumuls  = new float[n];

        float cumul = 0f;
        for (int i = 0; i < n; i++) {
            numbers[i] = sample.get(i).getNumber();
            cumul     += sample.get(i).getProbabilityOfSample();
            cumuls[i]  = cumul;
        }
        cumuls[n - 1] = 1.0f; // corrige les erreurs d'arrondi flottant

        return new ProbabilisticRandomGenImpl(numbers, cumuls, random);
    }

    /**
     *
     * cumuls  = [0.20, 0.50, 1.00]
     * probabilities = [0.2,    0.3,    0.5   ]
     * nombres = [3,    7,    9   ]
     *
     * rand=0.15 → ≤ 0.20 → retourne 3
     * rand=0.35 → ≤ 0.50 → retourne 7
     * rand=0.80 → ≤ 1.00 → retourne 9
     */
    @Override
    public int nextFromSample() {
        // On tire un nombre au hasard entre 0.0 et 1.0
        // Exemple : rand = 0.35
        float rand = random.nextFloat();

        // On parcourt chaque seuil cumulé dans l'ordre
        // cumuls  = [0.20, 0.50, 1.00]
        // nombres = [3,    7,    9   ]
        for (int i = 0; i < cumulativeProbabilities.length; i++) {
            if (rand <= cumulativeProbabilities[i]) {
                // 0.35 <= 0.20 ? NON
                // 0.35 <= 0.50 ? OUI → on retourne 7
                return numbers[i];
            }
        }

        // Ne devrait jamais arriver car le dernier cumul vaut toujours 1.0
        // et rand est toujours < 1.0
        return numbers[numbers.length - 1];
    }

    // ── Validation ─────────────────────────────────────────────────────────

    private static void validate(List<NumAndProbability> sample) {
        if (sample == null || sample.isEmpty())
            throw new IllegalArgumentException("La liste ne peut pas être vide.");

        float sum = 0f;
        for (NumAndProbability np : sample) {
            if (np.getProbabilityOfSample() < 0)
                throw new IllegalArgumentException(
                        "Probabilité négative interdite : " + np.getNumber());
            sum += np.getProbabilityOfSample();
        }

        if (Math.abs(sum - 1.0f) > 1e-5f)
            throw new IllegalArgumentException(
                    "La somme des probabilités doit être 1.0, obtenu : " + sum);
    }
}
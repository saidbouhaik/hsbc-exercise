package hsbc.exercise.throttler;

import java.util.concurrent.*;

public class Throttler implements IThrottler{

    private final int maxOperations;
    private final long timeWindowMillis;
    private final BlockingQueue<Long> requestTimestamps;
    // Exemple du restaurant. soit moi en tant que client je demande explicitement je peux rentrer (proceed)
    // ou j'attend que le vigile me dise tu peux rentrer (le vigile a chaque milliseconds il vas checker s'il y a de la place rentrer)
    private final ScheduledExecutorService scheduler; // ==> vigile dans un restaurant
    private Object lock = new Object();

    public Throttler(int maxOperations, long timeWindowMillis) {
        this.maxOperations = maxOperations;
        this.timeWindowMillis = timeWindowMillis;
        this.requestTimestamps = new LinkedBlockingQueue<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }


    /**
     * Mode Poll — le client demande s'il peut agir maintenant.
     *
     * Analogie restaurant :
     *   - le client demande au vigile "est-ce qu'il y a de la place ?"
     *   - synchronized → un seul vigile répond à la fois (pas de double entrée)
     */
    @Override
    public ThrottleResult shouldProceed() {
        long currentTime = System.currentTimeMillis();

        // size() n'est pas thread safe. dans notre exemple du restau, au cas ou il y aura 2, 3 vigils et tous ils disent qu'il y a une place
        // libre. c'est pour garantir qu'une seule personne qui vas rentrer
        synchronized (lock) {
            // 1- je supprimer les events dont le timestamp est suppérieur a timeWindowMillis
            cleanupOldRequests(currentTime);

            // check si la taille est inférieur a la maxWindow
            if (requestTimestamps.size() < maxOperations) {
                // l'ajout de l'element dans la queue
                requestTimestamps.offer(currentTime);
                return ThrottleResult.PROCEED;
            }
            else {
                return ThrottleResult.DO_NOT_PROCEED;
            }
        }
    }

    /**
     * Supprime les timestamps hors de la fenêtre glissante.
     * Doit être appelé dans un bloc synchronized.
     *
     * Exemple : timeWindow=1000ms, currentTime=5000ms
     *   → supprime tous les timestamps < 4000ms
     */
    private void cleanupOldRequests(long currentTime) {
        // requestTimestamps.peek() ==> c'est pour lire l'element de la queue sans le supprimer
        while (!requestTimestamps.isEmpty() && currentTime - requestTimestamps.peek() > timeWindowMillis) {
            requestTimestamps.poll(); // supprimer l'element de la queue
        }
    }

    /**
     * Mode Push — le client s'abonne pour être notifié quand il pourra agir.
     * Le Callback est le subscriber : il sera appelé dès qu'un slot est libre.
     *
     * Analogie restaurant :
     *   - le client laisse son numéro au vigile
     *   - le vigile le rappelle quand une table se libère
     */
    @Override
    public void notifyWhenCanProceed(Callback callback) {
        if (shouldProceed() == ThrottleResult.PROCEED) {
            callback.onProceed();
        } else {
            scheduler.schedule(() -> {
                waitUntilCanProceed(callback);
            }, 1, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Boucle d'attente active jusqu'à ce qu'un slot se libère.
     * Appelée depuis le scheduler → ne bloque pas le thread appelant.
     */
    private void waitUntilCanProceed(Callback callback) {
        try {
            while (shouldProceed() == ThrottleResult.DO_NOT_PROCEED) {
                Thread.sleep(100);
            }
            callback.onProceed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
    }
}

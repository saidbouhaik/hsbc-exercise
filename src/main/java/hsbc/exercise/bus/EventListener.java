package hsbc.exercise.bus;

// On aurait pu utiliser un consumer (la méthode accept du consumer c'est la même de OnEvent)
//Consumer est une interface de java qui accept un objet et qui ne retourne rien : comme foreach
public interface EventListener {
    void OnEvent(Object eventType);
}

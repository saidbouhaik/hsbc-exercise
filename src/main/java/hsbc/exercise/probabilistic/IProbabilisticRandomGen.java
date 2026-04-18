package hsbc.exercise.probabilistic;

import hsbc.exercise.exception.ProbabilityException;

public interface IProbabilisticRandomGen {
    int nextFromSample() throws ProbabilityException;
}

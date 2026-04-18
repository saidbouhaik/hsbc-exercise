package hsbc.exercise.throttler;

public interface IThrottler {

    ThrottleResult shouldProceed();

    void notifyWhenCanProceed(Callback callback);

    void shutdown();
}

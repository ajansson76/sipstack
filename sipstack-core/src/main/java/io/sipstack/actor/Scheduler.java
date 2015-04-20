/**
 * 
 */
package io.sipstack.actor;

import io.sipstack.event.Event;
import io.sipstack.timers.SipTimer;

import java.time.Duration;

/**
 * @author jonas@jonasborjesson.com
 */
public interface Scheduler {

    /**
     * Schedule an event to be passed to the current {@link Actor} after a specific delay pretending
     * to be an upstream event. Hence, when the delay has passed, this event will be passed through
     * the current {@link PipeLine} (as saved at the time when the Actor called this schedule
     * method) as if this event was just another upstream event.
     * 
     * @param delay the time delay.
     * @param event the event to pass to the actor after the delay has elapsed.
     */
    // TimerEvent scheduleUpstreamEventOnce(Duration delay, Object event);

    /**
     * Same as {@link #scheduleUpstreamEventOnce(Duration, Event)} but the direction of the event is
     * different.
     * 
     * @param delay
     * @param event
     */
    // TimerEvent scheduleDownstreamEventOnce(Duration delay, Object msg);

    // Cancellable schedule(Duration delay, Object msg);

    // Cancellable schedule(Duration delay, Object object);

    Cancellable schedule(Duration delay, SipTimer timer);

    Cancellable schedule(Duration delay, Event event);

    interface Cancellable {
        boolean cancel();

    }

    interface Work {
        void doWork(final ActorContext ctx, final PipeLine pipeLine);
    }
}
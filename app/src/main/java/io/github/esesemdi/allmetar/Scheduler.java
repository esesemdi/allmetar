package io.github.esesemdi.allmetar;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/**
 * Hourly background update.
 *
 * Routine METARs come out on the hour (a few minutes before it in the US), so the update is
 * timed for a few minutes past each hour rather than at an arbitrary minute. Each run schedules
 * the next one.
 */
final class Scheduler {
    static final int JOB_HOURLY = 1;
    static final int JOB_SOON = 2;

    /** Minutes past the hour at which the update is due. */
    private static final int MINUTE = 6;
    private static final long HOUR_MS = 3600_000L;

    private Scheduler() {
    }

    /** Makes sure an hourly update is pending. Cheap; safe to call often. */
    static void ensure(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js != null && js.getPendingJob(JOB_HOURLY) == null) {
                scheduleNext(c);
            }
        } catch (RuntimeException ignored) {
            // scheduling problems must not crash a screen; the next call tries again
        }
    }

    static void scheduleNext(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) {
            return;
        }
        long delay = delayToNextRun(System.currentTimeMillis());
        JobInfo job = new JobInfo.Builder(JOB_HOURLY, new ComponentName(c, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delay)
                // run by then even without a connection, so the chain of hourly runs never stops
                .setOverrideDeadline(delay + 25 * 60_000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    /** One extra update as soon as there is a connection. */
    static void runSoon(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) {
                return;
            }
            JobInfo job = new JobInfo.Builder(JOB_SOON, new ComponentName(c, UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(0)
                    .setOverrideDeadline(10 * 60_000L)
                    .build();
            js.schedule(job);
        } catch (RuntimeException ignored) {
            // see ensure()
        }
    }

    /** Milliseconds from {@code nowMs} to the next "MINUTE past the hour" that is at least 5 minutes away. */
    static long delayToNextRun(long nowMs) {
        long intoHour = nowMs % HOUR_MS;
        long target = MINUTE * 60_000L;
        long delay = target - intoHour;
        if (delay < 5 * 60_000L) {
            delay += HOUR_MS;
        }
        return delay;
    }
}

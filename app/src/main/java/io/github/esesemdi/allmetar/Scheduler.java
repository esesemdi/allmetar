package io.github.esesemdi.allmetar;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * Background updates.
 *
 * The main update runs once an hour, timed for when the hour's routine METARs are available:
 * US reports reach the feed a few minutes before the hour, Canadian ones about eight minutes
 * after it. Each run schedules the next. If a report is late, a few light follow-up checks are
 * made; if there was no connection, one more update runs as soon as it is back.
 */
final class Scheduler {
    /** Hourly update of everything. */
    static final int JOB_HOURLY = 1;
    /** One extra full update as soon as possible. */
    static final int JOB_SOON = 2;
    /** Light re-check for an hourly report that was not out yet. */
    static final int JOB_FOLLOW_UP = 3;
    /** Full update when the connection returns after a failed one. */
    static final int JOB_ONLINE = 4;

    static final String EXTRA_ATTEMPT = "attempt";
    static final int MAX_FOLLOW_UPS = 3;

    /** Minutes past the hour at which the hourly update is due. */
    private static final int MINUTE = 10;
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long FOLLOW_UP_DELAY_MS = 6 * MINUTE_MS;

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
        js.schedule(job(c, JOB_HOURLY)
                .setMinimumLatency(delay)
                // run by then even without a connection, so the chain of hourly runs never stops
                .setOverrideDeadline(delay + 20 * MINUTE_MS)
                .setPersisted(true)
                .build());
    }

    /** One extra update as soon as there is a connection. */
    static void runSoon(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js != null && js.getPendingJob(JOB_SOON) == null) {
                js.schedule(job(c, JOB_SOON)
                        .setMinimumLatency(0)
                        .setOverrideDeadline(10 * MINUTE_MS)
                        .build());
            }
        } catch (RuntimeException ignored) {
            // see ensure()
        }
    }

    /** Checks again in a few minutes for an hourly report that was late. */
    static void followUp(Context c, int attempt) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) {
            return;
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_ATTEMPT, attempt);
        js.schedule(job(c, JOB_FOLLOW_UP)
                .setMinimumLatency(FOLLOW_UP_DELAY_MS)
                .setExtras(extras)
                .build());
    }

    /** Updates as soon as the connection is back; the system retries with growing pauses. */
    static void whenOnline(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ONLINE) != null) {
            return;
        }
        js.schedule(job(c, JOB_ONLINE)
                .setMinimumLatency(30_000L)
                .setBackoffCriteria(2 * MINUTE_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build());
    }

    private static JobInfo.Builder job(Context c, int id) {
        return new JobInfo.Builder(id, new ComponentName(c, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    }

    /** Milliseconds from {@code nowMs} to the next "MINUTE past the hour" that is at least 4 minutes away. */
    static long delayToNextRun(long nowMs) {
        long intoHour = nowMs % HOUR_MS;
        long delay = MINUTE * MINUTE_MS - intoHour;
        if (delay < 4 * MINUTE_MS) {
            delay += HOUR_MS;
        }
        return delay;
    }
}

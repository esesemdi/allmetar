package io.github.esesemdi.allmetar;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

/** Runs the background update and keeps the hourly chain going. */
public final class UpdateJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                App.sync(app).refreshAll();
            } catch (RuntimeException ignored) {
                // reported per location in the store; nothing else to do here
            } finally {
                jobFinished(params, false);
                if (params.getJobId() == Scheduler.JOB_HOURLY) {
                    try {
                        Scheduler.scheduleNext(app);
                    } catch (RuntimeException ignored) {
                        // Scheduler.ensure() from the app or widget restores the chain
                    }
                }
            }
        }, "allmetar-update").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Interrupted by the system: ask for the hourly job to be retried so the chain continues.
        return params.getJobId() == Scheduler.JOB_HOURLY;
    }
}

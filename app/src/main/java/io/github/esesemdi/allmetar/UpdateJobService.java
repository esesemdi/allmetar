package io.github.esesemdi.allmetar;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import io.github.esesemdi.allmetar.core.Freshness;
import io.github.esesemdi.allmetar.core.Store;

/** Runs background updates and decides what to schedule afterwards. See {@link Scheduler}. */
public final class UpdateJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            final int job = params.getJobId();
            final long started = App.now();
            boolean weatherOk = false;
            try {
                if (job == Scheduler.JOB_FOLLOW_UP) {
                    App.sync(app).refreshLatest();
                } else {
                    App.sync(app).refreshAll();
                }
                Store store = App.store(app);
                weatherOk = store.ids().isEmpty() || store.lastRefresh() >= started;
            } catch (RuntimeException ignored) {
                // problems are recorded per location in the store
            } finally {
                // only the "when online" job asks the system to retry it
                jobFinished(params, job == Scheduler.JOB_ONLINE && !weatherOk);
                try {
                    if (job == Scheduler.JOB_HOURLY) {
                        Scheduler.scheduleNext(app);
                    }
                    if (!weatherOk && job != Scheduler.JOB_ONLINE) {
                        Scheduler.whenOnline(app);
                    }
                    int attempt = params.getExtras().getInt(Scheduler.EXTRA_ATTEMPT, 0);
                    if (weatherOk && attempt < Scheduler.MAX_FOLLOW_UPS
                            && Freshness.awaitingHourly(App.store(app).stations(), App.now())) {
                        Scheduler.followUp(app, attempt + 1);
                    }
                } catch (RuntimeException ignored) {
                    // Scheduler.ensure() from the app or widget restores the hourly chain
                }
            }
        }, "allmetar-update").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Interrupted by the system: have the hourly and when-online jobs retried.
        return params.getJobId() == Scheduler.JOB_HOURLY || params.getJobId() == Scheduler.JOB_ONLINE;
    }

    @Override
    public void onNetworkChanged(JobParameters params) {
        // nothing to do: a download that loses its connection fails and is retried
    }
}

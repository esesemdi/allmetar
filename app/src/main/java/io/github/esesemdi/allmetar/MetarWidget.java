package io.github.esesemdi.allmetar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.Station;
import io.github.esesemdi.allmetar.core.Store;

/** Home-screen widget: the current METAR of one saved location. */
public final class MetarWidget extends AppWidgetProvider {
    /** The widget says so when no update has succeeded for this long (seconds). */
    private static final long NO_UPDATE_NOTE_AFTER = 100 * 60;
    /** An extra update is requested when the data is older than this (seconds). */
    private static final long STALE_AFTER = 75 * 60;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            render(context, manager, widgetId);
        }
        Scheduler.ensure(context);
        Store store = App.store(context);
        if (!store.ids().isEmpty() && App.now() - store.lastRefresh() > STALE_AFTER) {
            Scheduler.runSoon(context);
        }
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        Store store = App.store(context);
        for (int widgetId : widgetIds) {
            store.setWidgetStation(widgetId, null);
        }
    }

    /** Redraws every placed widget from stored data. */
    static void updateAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager == null) {
                return;
            }
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, MetarWidget.class));
            for (int widgetId : ids) {
                render(context, manager, widgetId);
            }
        } catch (RuntimeException ignored) {
            // a launcher problem must not break the app
        }
    }

    static void render(Context context, AppWidgetManager manager, int widgetId) {
        Store store = App.store(context);
        String stationId = store.widgetStation(widgetId);
        Station s = stationId == null ? null : store.get(stationId);
        long now = App.now();

        CharSequence text;
        int color = 0xFFFFFFFF;
        String note = null;
        Intent tap;
        if (s == null) {
            text = stationId == null ? "Tap to choose a location"
                    : stationId + " is no longer in your list. Tap to choose another.";
            tap = new Intent(context, WidgetConfigActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData(Uri.parse("allmetar://widget/" + widgetId));
        } else {
            Metar m = s.latestMetar();
            if (m != null) {
                text = ReportStyle.metar(m.raw);
                color = ReportStyle.color(m, color);
            } else if (s.metarAt == 0) {
                text = s.id + "  loading…";
            } else {
                text = s.id + "  no METAR reported";
            }
            long last = Math.max(s.metarAt, 0);
            if (last > 0 && now - last > NO_UPDATE_NOTE_AFTER) {
                note = "! NO UPDATE SINCE " + Ui.dayHm(last);
            }
            tap = new Intent(context, DetailActivity.class)
                    .putExtra(DetailActivity.EXTRA_ID, s.id)
                    .setData(Uri.parse("allmetar://station/" + s.id));
        }
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_metar);
        views.setTextViewText(R.id.widget_text, text);
        views.setTextColor(R.id.widget_text, color);
        views.setTextViewText(R.id.widget_note, note == null ? "" : note);
        views.setViewVisibility(R.id.widget_note, note == null ? View.GONE : View.VISIBLE);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, widgetId, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(widgetId, views);
    }
}

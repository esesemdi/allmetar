package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.Station;
import io.github.esesemdi.allmetar.core.Store;
import io.github.esesemdi.allmetar.core.Sync;

/** The list of saved locations, each with its current METAR. */
public final class MainActivity extends Activity {
    /** Refresh on opening the app when the data is older than this (seconds). */
    static final long STALE_AFTER = 10 * 60;
    /** A METAR older than this is flagged as old (seconds). */
    static final long OLD_METAR = 2 * 3600 + 15 * 60;

    private LinearLayout list;
    private TextView status;
    private final Runnable onChange = this::render;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        TextView title = Ui.text(this, "ALLMETAR", 15, Ui.FG, false);
        title.setLetterSpacing(0.3f);
        bar.addView(title, Ui.weighted());
        bar.addView(Ui.action(this, "REFRESH", v -> refresh(true)));
        TextView addButton = Ui.action(this, "+", v -> add());
        addButton.setTextSize(24);
        addButton.setContentDescription("Add location");
        bar.addView(addButton);
        root.addView(bar, Ui.fill());
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(Ui.divider(this));
        status = Ui.text(this, "", 11, Ui.DIM, false);
        status.setLetterSpacing(0.1f);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(status, Ui.fill());

        setContentView(root);
        Scheduler.ensure(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addListener(onChange);
        render();
        refresh(false);
    }

    @Override
    protected void onStop() {
        App.removeListener(onChange);
        super.onStop();
    }

    private void add() {
        if (App.store(this).isFull()) {
            Toast.makeText(this, "The list holds " + Store.MAX_STATIONS + " locations. Remove one first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, AddActivity.class));
    }

    private void refresh(boolean byUser) {
        Store store = App.store(this);
        if (store.ids().isEmpty()) {
            return;
        }
        if (!byUser && (Sync.isBusy() || App.now() - store.lastRefresh() < STALE_AFTER)) {
            return;
        }
        App.refreshAll(this);
    }

    private void render() {
        Store store = App.store(this);
        List<Station> stations = store.stations();
        long now = App.now();
        list.removeAllViews();

        if (stations.isEmpty()) {
            TextView empty = Ui.text(this, "No locations yet.\n\nTap + to add up to " + Store.MAX_STATIONS
                    + " aerodromes.", 14, Ui.DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this, 32), Ui.dp(this, 96), Ui.dp(this, 32), Ui.dp(this, 32));
            list.addView(empty, Ui.fill());
        }

        String problem = null;
        for (final Station s : stations) {
            if (s.wxError != null) {
                problem = s.wxError;
            }
            LinearLayout item = Ui.column(this);
            int pad = Ui.dp(this, 16);
            item.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 14));
            Ui.ripple(item);

            LinearLayout head = Ui.row(this);
            TextView id = Ui.text(this, s.id, 17, Ui.FG, true);
            id.setTypeface(id.getTypeface(), android.graphics.Typeface.BOLD);
            head.addView(id, Ui.wrap());
            TextView name = Ui.oneLine(Ui.text(this, s.name, 12, Ui.DIM, false));
            name.setPadding(Ui.dp(this, 10), 0, 0, 0);
            head.addView(name, Ui.weighted());
            item.addView(head, Ui.fill());

            Metar m = s.latestMetar();
            CharSequence body;
            int color = Ui.FG;
            if (m != null) {
                body = ReportStyle.metar(m.raw);
                color = ReportStyle.color(m, Ui.FG);
            } else if (s.metarAt == 0) {
                body = s.wxError != null ? "Not loaded yet" : "Loading…";
                color = Ui.DIM;
            } else {
                body = "No METAR reported";
                color = Ui.DIM;
            }
            TextView raw = Ui.text(this, body, 13, color, true);
            raw.setPadding(0, Ui.dp(this, 6), 0, 0);
            item.addView(raw, Ui.fill());

            if (m != null && now - m.obs > OLD_METAR) {
                TextView old = Ui.text(this, "! OBSERVED " + Ui.age(m.obs, now) + " AGO", 11, Ui.FG, false);
                old.setLetterSpacing(0.1f);
                old.setPadding(0, Ui.dp(this, 6), 0, 0);
                item.addView(old, Ui.fill());
            }

            item.setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class)
                    .putExtra(DetailActivity.EXTRA_ID, s.id)));
            item.setOnLongClickListener(v -> {
                menu(s);
                return true;
            });
            list.addView(item, Ui.fill());
            list.addView(Ui.divider(this));
        }

        StringBuilder sb = new StringBuilder();
        long last = store.lastRefresh();
        if (Sync.isBusy()) {
            sb.append("UPDATING…");
        } else if (problem != null) {
            sb.append("! UPDATE FAILED: ").append(problem.toUpperCase(java.util.Locale.US));
            if (last > 0) {
                sb.append("  ·  DATA FROM ").append(Ui.dayHm(last));
            }
        } else if (last > 0) {
            sb.append("UPDATED ").append(Ui.dayHm(last));
        }
        if (!stations.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(stations.size()).append('/').append(Store.MAX_STATIONS);
        }
        status.setText(sb);
        status.setTextColor(problem != null && !Sync.isBusy() ? Ui.FG : Ui.DIM);
    }

    private void menu(final Station s) {
        final String[] items = {"Move up", "Move down", "Remove"};
        new AlertDialog.Builder(this)
                .setTitle(s.id)
                .setItems(items, (d, which) -> {
                    Store store = App.store(this);
                    if (which == 0) {
                        store.move(s.id, -1);
                    } else if (which == 1) {
                        store.move(s.id, 1);
                    } else {
                        store.remove(s.id);
                    }
                    App.notifyChanged(this);
                })
                .show();
    }
}

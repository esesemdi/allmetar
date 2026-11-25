package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import io.github.esesemdi.allmetar.core.Station;

/** Shown when a widget is placed (or tapped while unassigned): pick which location it displays. */
public final class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(root);
        TextView title = Ui.text(this, "WIDGET LOCATION", 15, Ui.FG, false);
        title.setLetterSpacing(0.2f);
        title.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        root.addView(title, Ui.fill());
        root.addView(Ui.divider(this));
        ScrollView scroll = new ScrollView(this);
        list = Ui.column(this);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (list != null) {
            render();
        }
    }

    private void render() {
        list.removeAllViews();
        List<Station> stations = App.store(this).stations();
        if (stations.isEmpty()) {
            TextView t = Ui.text(this, "Your list is empty. Add a location in the app first, then come back.",
                    14, Ui.DIM, false);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 16));
            list.addView(t, Ui.fill());
            TextView open = Ui.button(this, "Open AllMetar", v -> startActivity(new Intent(this, AddActivity.class)));
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.leftMargin = Ui.dp(this, 16);
            list.addView(open, lp);
            return;
        }
        for (final Station s : stations) {
            LinearLayout item = Ui.row(this);
            item.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
            Ui.ripple(item);
            TextView id = Ui.text(this, s.id, 17, Ui.FG, true);
            id.setTypeface(id.getTypeface(), android.graphics.Typeface.BOLD);
            item.addView(id, Ui.wrap());
            TextView name = Ui.oneLine(Ui.text(this, s.name, 13, Ui.DIM, false));
            name.setPadding(Ui.dp(this, 12), 0, 0, 0);
            item.addView(name, Ui.weighted());
            item.setOnClickListener(v -> choose(s.id));
            list.addView(item, Ui.fill());
            list.addView(Ui.divider(this));
        }
    }

    private void choose(String stationId) {
        App.store(this).setWidgetStation(widgetId, stationId);
        MetarWidget.render(this, AppWidgetManager.getInstance(this), widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}

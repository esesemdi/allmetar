package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import io.github.esesemdi.allmetar.core.Watch;

/** Weather watch: switch it on or off and set the targets that turn a report yellow. */
public final class WatchActivity extends Activity {
    private static final String[] NAMES = {"CEILING", "VISIBILITY", "TEMPERATURE", "WIND", "GUST"};
    private static final String[] UNITS = {"FT", "SM", "°C", "KT", "KT"};
    private static final String[] HINTS = {"Feet above ground, e.g. 3000", "Statute miles, e.g. 5",
            "Degrees C, e.g. -25", "Knots, e.g. 20", "Knots, e.g. 25"};

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        TextView back = Ui.action(this, "‹", v -> finish());
        back.setTextSize(24);
        back.setContentDescription("Back");
        bar.addView(back);
        TextView title = Ui.text(this, "WEATHER WATCH", 15, Ui.FG, false);
        title.setLetterSpacing(0.2f);
        bar.addView(title, Ui.weighted());
        root.addView(bar, Ui.fill());
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        list = Ui.column(this);
        list.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        render();
    }

    private void save(Watch w) {
        App.store(this).setWatch(w);
        App.notifyChanged(this);
        render();
    }

    private void render() {
        final Watch w = App.store(this).watch();
        list.removeAllViews();

        TextView toggle = Ui.button(this, w.on ? "Watch is on" : "Watch is off", v -> save(w.withOn(!w.on)));
        if (w.on) {
            toggle.setTextColor(ReportStyle.YELLOW);
        }
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.topMargin = Ui.dp(this, 20);
        list.addView(toggle, lp);

        TextView about = Ui.text(this, "While the watch is on, a METAR that meets any one of your targets is shown "
                + "in yellow, and so is each group of a TAF that does. LIFR stays red.", 13, Ui.DIM, false);
        about.setPadding(0, Ui.dp(this, 14), 0, 0);
        list.addView(about, Ui.fill());

        list.addView(Ui.heading(this, "Targets"), Ui.fill());
        for (int p = 0; p < Watch.COUNT; p++) {
            final int parameter = p;
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 13));
            Ui.ripple(row);
            row.addView(Ui.text(this, NAMES[p], 13, Ui.FG, true),
                    new LinearLayout.LayoutParams(Ui.dp(this, 128), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(Ui.text(this, describe(w, p), 13, w.isSet(p) ? Ui.FG : Ui.DIM, true), Ui.weighted());
            TextView arrow = Ui.text(this, "›", 16, Ui.DIM, false);
            arrow.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 2), 0);
            row.addView(arrow, Ui.wrap());
            row.setOnClickListener(v -> edit(parameter));
            list.addView(row, Ui.fill());
            list.addView(Ui.divider(this));
        }

        TextView notes = Ui.text(this, "TAFs carry no temperature, so that target applies to METARs only. "
                + "A gust target needs a reported gust. The targets apply to every location.", 12, Ui.DIM, false);
        notes.setPadding(0, Ui.dp(this, 16), 0, 0);
        list.addView(notes, Ui.fill());
    }

    private static String number(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String describe(Watch w, int p) {
        if (!w.isSet(p)) {
            return "NOT SET";
        }
        return (w.below(p) ? "AT OR BELOW " : "AT OR ABOVE ") + number(w.value(p)) + " " + UNITS[p];
    }

    private void edit(final int p) {
        final Watch w = App.store(this).watch();
        LinearLayout box = Ui.column(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final RadioGroup group = new RadioGroup(this);
        final RadioButton below = new RadioButton(this);
        below.setText("At or below");
        final int belowId = View.generateViewId();
        below.setId(belowId);
        final RadioButton above = new RadioButton(this);
        above.setText("At or above");
        final int aboveId = View.generateViewId();
        above.setId(aboveId);
        group.addView(below);
        group.addView(above);
        group.check(w.below(p) ? belowId : aboveId);
        box.addView(group, Ui.fill());

        final EditText value = new EditText(this);
        value.setHint(HINTS[p]);
        value.setSingleLine(true);
        value.setGravity(Gravity.START);
        int type = InputType.TYPE_CLASS_NUMBER;
        if (p == Watch.VISIBILITY || p == Watch.TEMPERATURE) {
            type |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
        }
        if (p == Watch.TEMPERATURE) {
            type |= InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
        value.setInputType(type);
        if (w.isSet(p)) {
            value.setText(number(w.value(p)));
        }
        box.addView(value, Ui.fill());

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(NAMES[p].charAt(0) + NAMES[p].substring(1).toLowerCase(Locale.US) + " target")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String text = value.getText().toString().trim().replace(',', '.');
                    double v;
                    try {
                        v = Double.parseDouble(text);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Not saved: enter a number.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (Double.isNaN(v) || Double.isInfinite(v) || (p != Watch.TEMPERATURE && v < 0)) {
                        Toast.makeText(this, "Not saved: that value can't be used.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    save(w.with(p, v, group.getCheckedRadioButtonId() != aboveId));
                    if (!w.on) {
                        Toast.makeText(this, "Saved. The watch is off; turn it on to use it.", Toast.LENGTH_LONG).show();
                    }
                });
        if (w.isSet(p)) {
            b.setNeutralButton("Clear", (d, which) -> save(w.without(p)));
        }
        b.show();
    }
}

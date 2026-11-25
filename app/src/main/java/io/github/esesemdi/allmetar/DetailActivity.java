package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import io.github.esesemdi.allmetar.core.Airport;
import io.github.esesemdi.allmetar.core.Freq;
import io.github.esesemdi.allmetar.core.History;
import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.Notam;
import io.github.esesemdi.allmetar.core.Runway;
import io.github.esesemdi.allmetar.core.Station;
import io.github.esesemdi.allmetar.core.Sync;
import io.github.esesemdi.allmetar.core.Taf;
import io.github.esesemdi.allmetar.core.TafFormat;

/** One location: aerodrome information, METARs and TAFs. */
public final class DetailActivity extends Activity {
    static final String EXTRA_ID = "id";
    static final String FAA_NOTAM_SEARCH = "https://notams.aim.faa.gov/notamSearch/";

    private static final String PREFS = "ui";
    private static final String KEY_TAB = "tab";
    private static final String[] TABS = {"AERODROME", "METAR", "TAF"};

    private String id;
    private int tab;
    private TextView nameView;
    private TextView status;
    private final TextView[] tabLabels = new TextView[TABS.length];
    private final View[] tabMarks = new View[TABS.length];
    private ScrollView scroll;
    private LinearLayout content;

    private Station shown;
    private boolean shownBusy;
    private int shownTab = -1;
    private final Runnable onChange = this::render;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null || App.store(this).get(id) == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        tab = state != null ? state.getInt(KEY_TAB, 0)
                : getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TAB, 0);
        if (tab < 0 || tab >= TABS.length) {
            tab = 0;
        }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        TextView back = Ui.action(this, "‹", v -> goBack());
        back.setTextSize(24);
        back.setContentDescription("Back");
        bar.addView(back);
        LinearLayout titles = Ui.column(this);
        TextView title = Ui.text(this, id, 19, Ui.FG, true);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titles.addView(title, Ui.fill());
        nameView = Ui.oneLine(Ui.text(this, "", 12, Ui.DIM, false));
        titles.addView(nameView, Ui.fill());
        bar.addView(titles, Ui.weighted());
        bar.addView(Ui.action(this, "REFRESH", v -> refresh()));
        root.addView(bar, Ui.fill());

        LinearLayout tabs = Ui.row(this);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            LinearLayout cell = Ui.column(this);
            Ui.ripple(cell);
            TextView label = Ui.text(this, TABS[i], 12, Ui.DIM, false);
            label.setLetterSpacing(0.14f);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
            cell.addView(label, Ui.fill());
            View mark = new View(this);
            cell.addView(mark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));
            cell.setOnClickListener(v -> selectTab(index));
            tabLabels[i] = label;
            tabMarks[i] = mark;
            tabs.addView(cell, Ui.weighted());
        }
        root.addView(tabs, Ui.fill());
        root.addView(Ui.divider(this));

        status = Ui.text(this, "", 11, Ui.DIM, false);
        status.setLetterSpacing(0.1f);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        status.setVisibility(View.GONE);
        root.addView(status, Ui.fill());

        scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (content == null) {
            return; // onCreate bailed out
        }
        App.addListener(onChange);
        shown = null;
        render();
        Station s = App.store(this).get(id);
        if (s != null && !Sync.isBusy() && App.now() - s.metarAt > MainActivity.STALE_AFTER) {
            refresh();
        }
    }

    @Override
    protected void onStop() {
        App.removeListener(onChange);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, tab);
    }

    private void goBack() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    private void refresh() {
        App.refreshStation(this, id);
    }

    private void selectTab(int index) {
        if (index == tab) {
            return;
        }
        tab = index;
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TAB, tab).apply();
        render();
        scroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------ rendering

    private void render() {
        Station s = App.store(this).get(id);
        if (s == null) {
            finish();
            return;
        }
        boolean busy = Sync.isBusy();
        if (s == shown && busy == shownBusy && tab == shownTab) {
            return;
        }
        shown = s;
        shownBusy = busy;
        shownTab = tab;
        long now = App.now();

        nameView.setText(s.name);
        for (int i = 0; i < TABS.length; i++) {
            tabLabels[i].setTextColor(i == tab ? Ui.FG : Ui.DIM);
            tabMarks[i].setBackgroundColor(i == tab ? Ui.FG : Ui.BG);
        }
        status.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(busy ? "UPDATING…" : "");

        content.removeAllViews();
        if (tab == 0) {
            aerodrome(s, now, busy);
        } else if (tab == 1) {
            metars(s, now, busy);
        } else {
            tafs(s, now, busy);
        }
    }

    private TextView add(TextView t) {
        content.addView(t, Ui.fill());
        return t;
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.DIM, false);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        return add(t);
    }

    private TextView warn(String s) {
        TextView t = Ui.text(this, "! " + s, 13, Ui.FG, false);
        t.setPadding(0, Ui.dp(this, 14), 0, 0);
        return add(t);
    }

    private TextView raw(String s, float sp, int color) {
        TextView t = Ui.text(this, s, sp, color, true);
        t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        t.setTextIsSelectable(true);
        return add(t);
    }

    private TextView small(String s) {
        TextView t = Ui.text(this, s, 11, Ui.DIM, false);
        t.setLetterSpacing(0.1f);
        return add(t);
    }

    private String pending(boolean busy) {
        return busy ? "Loading…" : "Not loaded yet. Tap REFRESH.";
    }

    // ------------------------------------------------------------ AERODROME

    private void aerodrome(final Station s, long now, boolean busy) {
        Airport a = s.airport;
        if (a != null) {
            TextView n = Ui.text(this, a.name, 16, Ui.FG, false);
            n.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 2));
            add(n);
            StringBuilder sb = new StringBuilder(a.place());
            if (!a.country.isEmpty()) {
                sb.append(sb.length() > 0 ? " · " : "").append(a.country);
            }
            if (!a.others.isEmpty()) {
                sb.append(sb.length() > 0 ? " · " : "").append(a.others.replace(" ", " / "));
            }
            note(sb.toString());
            StringBuilder geo = new StringBuilder();
            if (!a.elevFt.isEmpty()) {
                geo.append("ELEV ").append(a.elevFt).append(" FT");
            }
            String pos = position(a.lat, a.lon);
            if (!pos.isEmpty()) {
                geo.append(geo.length() > 0 ? "  ·  " : "").append(pos);
            }
            if (geo.length() > 0) {
                small(geo.toString());
            }
        } else {
            TextView n = Ui.text(this, "Not in the built-in aerodrome list — weather only.", 13, Ui.DIM, false);
            n.setPadding(0, Ui.dp(this, 18), 0, 0);
            add(n);
        }

        add(Ui.heading(this, "Frequencies"));
        boolean any = false;
        if (a != null) {
            for (Freq f : a.freqs) {
                freqRow(f, null);
                any = true;
            }
        }
        for (int i = 0; i < s.userFreqs.size(); i++) {
            final int index = i;
            final Freq f = s.userFreqs.get(i);
            freqRow(f, v -> {
                confirmRemoveFreq(f, index);
                return true;
            });
            any = true;
        }
        if (!any) {
            note("None on file.");
        }
        TextView addFreq = Ui.button(this, "+ Add frequency", v -> addFreqDialog());
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.topMargin = Ui.dp(this, 12);
        content.addView(addFreq, lp);
        if (a != null) {
            TextView src = small("FREQUENCIES AND RUNWAYS: OURAIRPORTS (COMMUNITY DATA). VERIFY IN THE CFS / CHART SUPPLEMENT.");
            src.setPadding(0, Ui.dp(this, 12), 0, 0);
        }

        add(Ui.heading(this, "Runways"));
        if (a == null || a.runways.isEmpty()) {
            note("None on file.");
        } else {
            for (Runway r : a.runways) {
                runwayRow(r);
            }
        }

        notams(s, now, busy);
    }

    private static String position(String lat, String lon) {
        try {
            double la = Double.parseDouble(lat);
            double lo = Double.parseDouble(lon);
            return String.format(Locale.US, "%.4f%s %.4f%s", Math.abs(la), la >= 0 ? "N" : "S",
                    Math.abs(lo), lo >= 0 ? "E" : "W");
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private void freqRow(Freq f, View.OnLongClickListener onLong) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        TextView type = Ui.text(this, f.type, 13, Ui.FG, true);
        row.addView(type, new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView mhz = Ui.text(this, f.mhzFormatted(), 13, Ui.FG, true);
        row.addView(mhz, new LinearLayout.LayoutParams(Ui.dp(this, 84), ViewGroup.LayoutParams.WRAP_CONTENT));
        String d = f.desc == null ? "" : f.desc;
        if (d.equalsIgnoreCase(f.type)) {
            d = "";
        }
        if (onLong != null) {
            d = d.isEmpty() ? "added by you" : d + " · added by you";
        }
        row.addView(Ui.text(this, d, 12, Ui.DIM, false), Ui.weighted());
        if (onLong != null) {
            row.setOnLongClickListener(onLong);
            Ui.ripple(row);
        }
        content.addView(row, Ui.fill());
    }

    private void runwayRow(Runway r) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        int color = r.closed ? Ui.DIM : Ui.FG;
        TextView des = Ui.text(this, r.designator(), 13, color, true);
        row.addView(des, new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
        StringBuilder sb = new StringBuilder();
        if (!r.lengthFt.isEmpty()) {
            sb.append(r.lengthFt);
            if (!r.widthFt.isEmpty()) {
                sb.append(" × ").append(r.widthFt);
            }
            sb.append(" FT");
        }
        if (!r.surface.isEmpty()) {
            sb.append(sb.length() > 0 ? "  " : "").append(r.surface);
        }
        if (r.lighted) {
            sb.append(sb.length() > 0 ? "  " : "").append("LGT");
        }
        if (r.closed) {
            sb.append(sb.length() > 0 ? "  " : "").append("CLOSED");
        }
        row.addView(Ui.text(this, sb.toString(), 13, color, true), Ui.weighted());
        content.addView(row, Ui.fill());
    }

    private void addFreqDialog() {
        LinearLayout box = Ui.column(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText label = new EditText(this);
        label.setHint("Label, e.g. ATF");
        label.setSingleLine(true);
        label.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        box.addView(label, Ui.fill());
        final EditText mhz = new EditText(this);
        mhz.setHint("Frequency in MHz, e.g. 123.2");
        mhz.setSingleLine(true);
        mhz.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(mhz, Ui.fill());
        new AlertDialog.Builder(this)
                .setTitle("Add frequency")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    final String l = label.getText().toString().trim().toUpperCase(Locale.US);
                    final String f = mhz.getText().toString().trim();
                    if (l.isEmpty() || f.isEmpty()) {
                        Toast.makeText(this, "Enter both a label and a frequency.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    App.store(this).update(id, e -> e.userFreqs.add(new Freq(l, "", f)));
                    App.notifyChanged(this);
                })
                .show();
    }

    private void confirmRemoveFreq(final Freq f, final int index) {
        new AlertDialog.Builder(this)
                .setMessage("Remove " + f.type + " " + f.mhzFormatted() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    App.store(this).update(id, e -> {
                        if (index < e.userFreqs.size() && e.userFreqs.get(index) == f) {
                            e.userFreqs.remove(index);
                        }
                    });
                    App.notifyChanged(this);
                })
                .show();
    }

    private void notams(Station s, long now, boolean busy) {
        String country = s.country();
        if (!"CA".equals(country)) {
            add(Ui.heading(this, "NOTAMs"));
            if ("US".equals(country)) {
                note("US NOTAMs can't be shown in the app: the FAA does not offer an open NOTAM feed. "
                        + "Look up " + s.id + " on the FAA NOTAM Search site.");
                TextView open = Ui.button(this, "Open FAA NOTAM Search", v -> openUrl(FAA_NOTAM_SEARCH));
                LinearLayout.LayoutParams lp = Ui.wrap();
                lp.topMargin = Ui.dp(this, 10);
                content.addView(open, lp);
            } else {
                note("NOTAMs are available in the app for Canadian aerodromes only.");
            }
            return;
        }

        int atAerodrome = 0;
        for (Notam n : s.notams) {
            if (n.aerodrome) {
                atAerodrome++;
            }
        }
        add(Ui.heading(this, s.notamAt > 0 ? "NOTAMs · " + s.notams.size() : "NOTAMs"));
        if (s.notamError != null) {
            TextView w = warn("NOTAM update failed: " + s.notamError
                    + (s.notamAt > 0 ? ". Showing NOTAMs as of " + Ui.dayHm(s.notamAt) + "." : "."));
            w.setPadding(0, 0, 0, Ui.dp(this, 8));
        }
        if (s.notamAt == 0) {
            if (s.notamError == null) {
                note(pending(busy));
            }
            return;
        }
        small("NAV CANADA · AS OF " + Ui.dayHm(s.notamAt));
        if (s.notams.isEmpty()) {
            note("No NOTAMs returned for this aerodrome.");
            return;
        }
        boolean inArea = false;
        boolean first = true;
        if (atAerodrome > 0) {
            subheading("AERODROME · " + atAerodrome);
        }
        for (Notam n : s.notams) {
            if (!n.aerodrome && !inArea) {
                inArea = true;
                first = true;
                subheading("FIR / AREA · " + (s.notams.size() - atAerodrome));
            }
            if (!first) {
                content.addView(Ui.divider(this));
            }
            first = false;
            raw(n.raw, 12, Ui.FG);
        }
    }

    private void subheading(String s) {
        TextView t = Ui.text(this, s, 11, Ui.FG, false);
        t.setLetterSpacing(0.14f);
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));
        add(t);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser available.", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ METAR

    private void metars(Station s, long now, boolean busy) {
        if (s.wxError != null) {
            warn("Weather update failed: " + s.wxError
                    + (s.metarAt > 0 ? ". Showing data as of " + Ui.dayHm(s.metarAt) + "." : "."));
        }
        add(Ui.heading(this, "Latest"));
        List<Metar> latest = History.latest(s.metars, 5);
        if (latest.isEmpty()) {
            note(s.metarAt == 0 ? pending(busy) : "No METARs reported for this location.");
        }
        for (int i = 0; i < latest.size(); i++) {
            if (i > 0) {
                content.addView(Ui.divider(this));
            }
            raw(latest.get(i).raw, 13, Ui.FG);
        }
        Metar newest = s.latestMetar();
        if (newest != null && now - newest.obs > MainActivity.OLD_METAR) {
            warn("Newest report is " + Ui.age(newest.obs, now).toLowerCase(Locale.US) + " old.");
        }

        add(Ui.heading(this, "Every 3 hours · past 7 days · UTC"));
        if (s.histTo == 0) {
            note(busy ? "Loading history…" : "History not loaded yet. Tap REFRESH.");
            return;
        }
        if (s.metars.isEmpty()) {
            note("No reports.");
            return;
        }
        String lastDay = null;
        for (History.Slot slot : History.threeHourly(s.metars, now, Sync.HISTORY_DAYS)) {
            String day = Ui.day(slot.time);
            if (!day.equals(lastDay)) {
                TextView d = Ui.text(this, day, 11, Ui.FG, false);
                d.setLetterSpacing(0.14f);
                d.setPadding(0, Ui.dp(this, lastDay == null ? 2 : 18), 0, Ui.dp(this, 2));
                add(d);
                lastDay = day;
            }
            if (slot.metar != null) {
                TextView t = raw(slot.metar.raw, 12, Ui.FG);
                t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            } else {
                TextView t = raw(Ui.hhmm(slot.time) + "  no report", 12, Ui.DIM);
                t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            }
        }
    }

    // ------------------------------------------------------------ TAF

    private void tafs(Station s, long now, boolean busy) {
        if (s.wxError != null) {
            warn("Weather update failed: " + s.wxError
                    + (s.tafAt > 0 ? ". Showing data as of " + Ui.dayHm(s.tafAt) + "." : "."));
        }
        add(Ui.heading(this, "Current"));
        Taf cur = s.latestTaf();
        if (cur == null) {
            note(s.tafAt == 0 ? pending(busy) : "No TAF issued for this aerodrome.");
            return;
        }
        if (cur.validTo > 0 && cur.validTo < now) {
            TextView w = warn("Expired " + Ui.dayHm(cur.validTo) + " and no newer TAF has been issued.");
            w.setPadding(0, 0, 0, Ui.dp(this, 6));
        }
        small("ISSUED " + Ui.dayHm(cur.issued));
        raw(TafFormat.lines(cur.raw), 13, Ui.FG);

        if (s.tafs.size() > 1) {
            add(Ui.heading(this, "Earlier"));
            for (int i = 1; i < s.tafs.size(); i++) {
                Taf t = s.tafs.get(i);
                if (i > 1) {
                    content.addView(Ui.divider(this));
                }
                TextView label = small("ISSUED " + Ui.dayHm(t.issued));
                label.setPadding(0, Ui.dp(this, i > 1 ? 10 : 0), 0, 0);
                raw(TafFormat.lines(t.raw), 12, Ui.DIM);
            }
        }
    }
}

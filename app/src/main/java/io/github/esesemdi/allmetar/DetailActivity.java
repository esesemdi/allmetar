package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import io.github.esesemdi.allmetar.core.Airport;
import io.github.esesemdi.allmetar.core.Decode;
import io.github.esesemdi.allmetar.core.Freq;
import io.github.esesemdi.allmetar.core.Graph;
import io.github.esesemdi.allmetar.core.History;
import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.Notam;
import io.github.esesemdi.allmetar.core.Note;
import io.github.esesemdi.allmetar.core.Runway;
import io.github.esesemdi.allmetar.core.Station;
import io.github.esesemdi.allmetar.core.Store;
import io.github.esesemdi.allmetar.core.Sync;
import io.github.esesemdi.allmetar.core.Taf;
import io.github.esesemdi.allmetar.core.TafFormat;

/** One location: aerodrome information, METARs and TAFs. */
public final class DetailActivity extends Activity {
    static final String EXTRA_ID = "id";
    static final String FAA_NOTAM_SEARCH = "https://notams.aim.faa.gov/notamSearch/";

    private static final String PREFS = "ui";
    private static final String KEY_TAB = "tab";
    private static final String KEY_EDITING = "editing";
    private static final String[] TABS = {"AERODROME", "METAR", "TAF"};

    private String id;
    private int tab;
    /** True while the aerodrome tab is in edit mode. */
    private boolean editing;
    private TextView nameView;
    private TextView status;
    private final TextView[] tabLabels = new TextView[TABS.length];
    private final View[] tabMarks = new View[TABS.length];
    private ScrollView scroll;
    private LinearLayout content;
    private PopupWindow popup;
    /** Set by a long press so that lifting the finger does not also count as a tap on the word. */
    private boolean longPressed;

    private Station shown;
    private boolean shownBusy;
    private boolean shownEditing;
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
        editing = state != null && state.getBoolean(KEY_EDITING, false);

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
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
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
        dismissPopup();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, tab);
        out.putBoolean(KEY_EDITING, editing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (popup != null) {
            dismissPopup();
        } else if (editing && tab == 0) {
            editing = false;
            render();
        } else {
            super.onBackPressed();
        }
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
        if (s == shown && busy == shownBusy && tab == shownTab && editing == shownEditing) {
            return;
        }
        shown = s;
        shownBusy = busy;
        shownTab = tab;
        shownEditing = editing;
        long now = App.now();
        dismissPopup();

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

    private TextView small(String s) {
        TextView t = Ui.text(this, s, 11, Ui.DIM, false);
        t.setLetterSpacing(0.1f);
        return add(t);
    }

    private TextView button(String label, View.OnClickListener l) {
        TextView b = Ui.button(this, label, l);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.topMargin = Ui.dp(this, 12);
        content.addView(b, lp);
        return b;
    }

    private String pending(boolean busy) {
        return busy ? "Loading…" : "Not loaded yet. Tap REFRESH.";
    }

    // ------------------------------------------------------------ raw text whose words can be pressed

    /**
     * Raw report text; pressing a word explains it, a long press copies the whole report.
     * Ceiling and visibility are marked; pass the TAF itself for TAF text so its groups can be timed.
     */
    private TextView words(final String text, float sp, int color, final int kind, Taf taf) {
        final TextView t = Ui.text(this, "", sp, color, true);
        t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        final int[][] ranges = Decode.wordRanges(text);
        final String[] tokens = new String[ranges.length];
        SpannableString span = new SpannableString(text);
        for (int k = 0; k < ranges.length; k++) {
            tokens[k] = text.substring(ranges[k][0], ranges[k][1]);
            final int index = k;
            span.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (longPressed) {
                        longPressed = false;
                        return;
                    }
                    explain(t, tokens, ranges, index, kind);
                }

                @Override
                public void updateDrawState(TextPaint paint) {
                    // leave the word looking like the rest of the report
                }
            }, ranges[k][0], ranges[k][1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (kind == Decode.METAR) {
            ReportStyle.markMetar(span, text);
        } else if (kind == Decode.TAF && taf != null) {
            ReportStyle.markTaf(span, text, taf.validFrom, taf.validTo);
        }
        t.setText(span, TextView.BufferType.SPANNABLE);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(0);
        t.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                longPressed = false;
            }
            return false;
        });
        t.setOnLongClickListener(v -> {
            longPressed = true;
            copy(text);
            return true;
        });
        return add(t);
    }

    private void copy(String text) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("AllMetar", text));
        if (Build.VERSION.SDK_INT < 33) { // newer Android versions show their own confirmation
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissPopup() {
        PopupWindow p = popup;
        popup = null;
        if (p != null) {
            try {
                p.dismiss();
            } catch (RuntimeException ignored) {
                // window already gone
            }
        }
    }

    /** Shows a small box above (or below) the pressed word with its meaning. */
    private void explain(TextView view, String[] tokens, int[][] ranges, int index, int kind) {
        dismissPopup();
        Layout layout = view.getLayout();
        if (layout == null || !(view.getText() instanceof Spannable)) {
            return;
        }
        Decode.Hit hit = Decode.decode(kind, tokens, index);
        int from = hit == null ? index : hit.from;
        int to = hit == null ? index : hit.to;
        StringBuilder heading = new StringBuilder();
        for (int k = from; k <= to; k++) {
            heading.append(k > from ? " " : "").append(tokens[k]);
        }

        final Spannable text = (Spannable) view.getText();
        final BackgroundColorSpan back = new BackgroundColorSpan(Ui.FG);
        final ForegroundColorSpan fore = new ForegroundColorSpan(Ui.BG);
        text.setSpan(back, ranges[from][0], ranges[to][1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(fore, ranges[from][0], ranges[to][1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int margin = Ui.dp(this, 12);
        int pad = Ui.dp(this, 12);
        int windowWidth = getWindow().getDecorView().getWidth();
        int windowHeight = getWindow().getDecorView().getHeight();
        int maxWidth = Math.min(windowWidth - 2 * margin, Ui.dp(this, 340));

        LinearLayout box = Ui.column(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Ui.BG);
        frame.setStroke(Math.max(1, Ui.dp(this, 1)), Ui.FG);
        box.setBackground(frame);
        box.setPadding(pad, pad, pad, pad);
        TextView head = Ui.text(this, heading, 13, Ui.FG, true);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        head.setMaxWidth(maxWidth - 2 * pad);
        box.addView(head, Ui.wrap());
        TextView body = Ui.text(this, hit == null ? "No decode for this word." : hit.text, 13,
                hit == null ? Ui.DIM : Ui.FG, false);
        body.setMaxWidth(maxWidth - 2 * pad);
        body.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(body, Ui.wrap());
        box.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int boxWidth = box.getMeasuredWidth();
        int boxHeight = box.getMeasuredHeight();

        int[] at = new int[2];
        view.getLocationInWindow(at);
        int line = layout.getLineForOffset(ranges[index][0]);
        int wordX = at[0] + view.getTotalPaddingLeft() + (int) layout.getPrimaryHorizontal(ranges[index][0]);
        int lineTop = at[1] + view.getTotalPaddingTop() + layout.getLineTop(line);
        int lineBottom = at[1] + view.getTotalPaddingTop() + layout.getLineBottom(line);
        int[] area = new int[2];
        scroll.getLocationInWindow(area);
        int gap = Ui.dp(this, 6);
        int x = Math.max(margin, Math.min(wordX, windowWidth - boxWidth - margin));
        int y = lineTop - boxHeight - gap;
        if (y < area[1]) {
            y = lineBottom + gap;
        }
        y = Math.max(margin, Math.min(y, windowHeight - boxHeight - margin));

        // Exact size: a wrap-content window is laid out narrower than this measurement on some
        // devices, which would re-wrap the text and make the box taller than the space reserved.
        final PopupWindow p = new PopupWindow(box, boxWidth, boxHeight, false);
        p.setBackgroundDrawable(new ColorDrawable(0));
        p.setOutsideTouchable(true);
        p.setOnDismissListener(() -> {
            text.removeSpan(back);
            text.removeSpan(fore);
            if (popup == p) {
                popup = null;
            }
        });
        box.setOnClickListener(v -> dismissPopup());
        try {
            p.showAtLocation(view, Gravity.NO_GRAVITY, x, y);
            popup = p;
        } catch (RuntimeException e) {
            text.removeSpan(back);
            text.removeSpan(fore);
        }
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
        } else {
            TextView n = Ui.text(this, "Not in the built-in aerodrome list. Use EDIT to enter its data yourself.",
                    13, Ui.DIM, false);
            n.setPadding(0, Ui.dp(this, 18), 0, 0);
            add(n);
        }
        StringBuilder geo = new StringBuilder();
        String elev = s.elevFt();
        if (!elev.isEmpty()) {
            geo.append("ELEV ").append(elev).append(" FT").append(s.elevEdit != null ? " (YOURS)" : "");
        }
        String pos = a == null ? "" : position(a.lat, a.lon);
        if (!pos.isEmpty()) {
            geo.append(geo.length() > 0 ? "  ·  " : "").append(pos);
        }
        if (geo.length() > 0) {
            small(geo.toString());
        }

        button(editing ? "Done editing" : "Edit aerodrome data", v -> {
            editing = !editing;
            render();
        });
        if (editing) {
            TextView hint = small("TAP A LINE TO CHANGE OR REMOVE IT.");
            hint.setPadding(0, Ui.dp(this, 12), 0, 0);
            line("ELEVATION", elev.isEmpty() ? "not set" : elev + " FT", "", false, v -> editElevation(s));
        }

        add(Ui.heading(this, "Frequencies"));
        List<Freq> freqs = s.freqs();
        for (final Freq f : freqs) {
            String d = f.desc == null ? "" : f.desc;
            if (d.equalsIgnoreCase(f.type)) {
                d = "";
            }
            line(f.type, f.mhzFormatted(), d, f.mine, editing ? v -> editFreq(s, f) : null);
        }
        if (freqs.isEmpty()) {
            note("None on file.");
        }
        if (editing) {
            button("+ Add frequency", v -> editFreq(s, null));
        }

        add(Ui.heading(this, "Runways"));
        List<Runway> runways = s.runways();
        for (final Runway r : runways) {
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
            TextView[] cells = line(r.designator(), sb.toString(), "", r.mine,
                    editing ? v -> editRunway(s, r) : null);
            if (r.closed) {
                cells[0].setTextColor(Ui.DIM);
                cells[1].setTextColor(Ui.DIM);
            }
        }
        if (runways.isEmpty()) {
            note("None on file.");
        }
        if (editing) {
            button("+ Add runway", v -> editRunway(s, null));
        }

        for (final Note n : s.notes) {
            add(Ui.heading(this, n.header.isEmpty() ? "Note" : n.header));
            TextView body = Ui.text(this, n.text, 14, Ui.FG, false);
            body.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            if (editing) {
                Ui.ripple(body);
                body.setOnClickListener(v -> editNote(s, n));
            } else {
                body.setTextIsSelectable(true);
            }
            add(body);
        }
        if (editing) {
            add(Ui.heading(this, "Your own fields"));
            note("Add anything you want to keep with this aerodrome: circuit, fuel, phone numbers, procedures.");
            button("+ Add field", v -> editNote(s, null));
            if (s.hasEdits()) {
                TextView reset = button("Reset to built-in data", v -> confirmReset());
                ((LinearLayout.LayoutParams) reset.getLayoutParams()).topMargin = Ui.dp(this, 28);
            }
        }

        TextView src = small(a != null
                ? "BUILT-IN FREQUENCIES AND RUNWAYS: OURAIRPORTS (COMMUNITY DATA). LINES MARKED YOURS WERE ENTERED BY YOU. VERIFY IN THE CFS / CHART SUPPLEMENT."
                : "LINES MARKED YOURS WERE ENTERED BY YOU. VERIFY IN THE CFS / CHART SUPPLEMENT.");
        src.setPadding(0, Ui.dp(this, 20), 0, 0);

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

    /** One three-column line of aerodrome data; returns its first two text views. */
    private TextView[] line(String first, String second, String rest, boolean mine, View.OnClickListener onClick) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        TextView a = Ui.text(this, first, 13, Ui.FG, true);
        row.addView(a, new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = Ui.text(this, second, 13, Ui.FG, true);
        boolean wide = rest.isEmpty();
        row.addView(b, wide ? Ui.weighted()
                : new LinearLayout.LayoutParams(Ui.dp(this, 84), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!wide) {
            row.addView(Ui.text(this, rest, 12, Ui.DIM, false), Ui.weighted());
        }
        if (mine) {
            TextView tag = Ui.text(this, "YOURS", 10, Ui.DIM, false);
            tag.setLetterSpacing(0.1f);
            tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), 0, 0);
            row.addView(tag, Ui.wrap());
        }
        if (onClick != null) {
            TextView arrow = Ui.text(this, "›", 16, Ui.DIM, false);
            arrow.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 2), 0);
            row.addView(arrow, Ui.wrap());
            row.setOnClickListener(onClick);
            Ui.ripple(row);
        }
        content.addView(row, Ui.fill());
        return new TextView[]{a, b};
    }

    // ------------------------------------------------------------ editing

    private EditText input(LinearLayout box, String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setInputType(type);
        if ((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        box.addView(e, Ui.fill());
        return e;
    }

    private LinearLayout form() {
        LinearLayout box = Ui.column(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        return box;
    }

    private static String value(EditText e) {
        return e.getText().toString().trim();
    }

    private void change(Store.Change change) {
        App.store(this).update(id, change);
        App.notifyChanged(this);
    }

    private void editFreq(Station s, final Freq old) {
        LinearLayout box = form();
        final EditText label = input(box, "Label, e.g. ATF", old == null ? "" : old.type,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText mhz = input(box, "Frequency in MHz, e.g. 123.2", old == null ? "" : old.mhz,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText desc = input(box, "Description (optional)",
                old == null || old.desc.equalsIgnoreCase(old.type) ? "" : old.desc,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(old == null ? "Add frequency" : "Edit frequency")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String l = value(label).toUpperCase(Locale.US);
                    final String f = value(mhz);
                    final String ds = value(desc);
                    if (l.isEmpty() || f.isEmpty()) {
                        Toast.makeText(this, "Not saved: enter a label and a frequency.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    change(e -> {
                        List<Freq> list = e.editableFreqs();
                        Freq next = new Freq(l, ds, f, true);
                        int at = old == null ? -1 : list.indexOf(old);
                        if (at >= 0) {
                            list.set(at, next);
                        } else {
                            list.add(next);
                        }
                    });
                });
        if (old != null) {
            b.setNeutralButton("Remove", (d, w) -> change(e -> e.editableFreqs().remove(old)));
        }
        b.show();
    }

    private void editRunway(Station s, final Runway old) {
        LinearLayout box = form();
        final EditText ends = input(box, "Runway, e.g. 18/36", old == null ? "" : old.designator(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText length = input(box, "Length in feet", old == null ? "" : old.lengthFt,
                InputType.TYPE_CLASS_NUMBER);
        final EditText width = input(box, "Width in feet", old == null ? "" : old.widthFt,
                InputType.TYPE_CLASS_NUMBER);
        final EditText surface = input(box, "Surface, e.g. ASPHALT", old == null ? "" : old.surface,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final CheckBox lighted = new CheckBox(this);
        lighted.setText("Lighted");
        lighted.setChecked(old != null && old.lighted);
        box.addView(lighted, Ui.fill());
        final CheckBox closed = new CheckBox(this);
        closed.setText("Closed");
        closed.setChecked(old != null && old.closed);
        box.addView(closed, Ui.fill());
        ScrollView wrap = new ScrollView(this);
        wrap.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(old == null ? "Add runway" : "Edit runway")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String designator = value(ends).toUpperCase(Locale.US).replace(" ", "");
                    if (designator.isEmpty()) {
                        Toast.makeText(this, "Not saved: enter the runway, e.g. 18/36.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int slash = designator.indexOf('/');
                    final String low = slash < 0 ? designator : designator.substring(0, slash);
                    final String high = slash < 0 ? "" : designator.substring(slash + 1);
                    final Runway next = new Runway(low, high, value(length), value(width),
                            value(surface).toUpperCase(Locale.US), lighted.isChecked(), closed.isChecked(), true);
                    change(e -> {
                        List<Runway> list = e.editableRunways();
                        int at = old == null ? -1 : list.indexOf(old);
                        if (at >= 0) {
                            list.set(at, next);
                        } else {
                            list.add(next);
                        }
                    });
                });
        if (old != null) {
            b.setNeutralButton("Remove", (d, w) -> change(e -> e.editableRunways().remove(old)));
        }
        b.show();
    }

    private void editElevation(Station s) {
        LinearLayout box = form();
        final EditText feet = input(box, "Elevation in feet", s.elevFt(), InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Elevation")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String v = value(feet);
                    change(e -> {
                        boolean same = e.airport != null && e.airport.elevFt.equals(v);
                        e.elevEdit = v.isEmpty() || same ? null : v;
                    });
                })
                .show();
    }

    private void editNote(Station s, final Note old) {
        LinearLayout box = form();
        final EditText header = input(box, "Heading, e.g. Circuit", old == null ? "" : old.header,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final EditText text = input(box, "Text", old == null ? "" : old.text,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ScrollView wrap = new ScrollView(this);
        wrap.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(old == null ? "Add field" : "Edit field")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String h = value(header);
                    final String t = value(text);
                    if (h.isEmpty() && t.isEmpty()) {
                        Toast.makeText(this, "Not saved: the field is empty.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    change(e -> {
                        Note next = new Note(h, t);
                        int at = old == null ? -1 : e.notes.indexOf(old);
                        if (at >= 0) {
                            e.notes.set(at, next);
                        } else {
                            e.notes.add(next);
                        }
                    });
                });
        if (old != null) {
            b.setNeutralButton("Remove", (d, w) -> change(e -> e.notes.remove(old)));
        }
        b.show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset to built-in data?")
                .setMessage("Your changes to the frequencies, runways and elevation of " + id
                        + " will be discarded. Fields you added yourself are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> change(Station.Edit::resetToBuiltIn))
                .show();
    }

    // ------------------------------------------------------------ NOTAMs

    private void notams(Station s, long now, boolean busy) {
        String country = s.country();
        if (!"CA".equals(country)) {
            add(Ui.heading(this, "NOTAMs"));
            if ("US".equals(country)) {
                note("US NOTAMs can't be shown in the app: the FAA does not offer an open NOTAM feed. "
                        + "Look up " + s.id + " on the FAA NOTAM Search site.");
                button("Open FAA NOTAM Search", v -> openUrl(FAA_NOTAM_SEARCH));
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
        small("NAV CANADA · AS OF " + Ui.dayHm(s.notamAt) + (s.notams.isEmpty() ? "" : " · TAP A WORD TO DECODE IT"));
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
            words(n.raw, 12, Ui.FG, Decode.NOTAM, null);
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
        } else {
            small("TAP A WORD TO DECODE IT · RED = SPECI OR LIFR");
        }
        for (int i = 0; i < latest.size(); i++) {
            if (i > 0) {
                content.addView(Ui.divider(this));
            }
            Metar m = latest.get(i);
            words(m.raw, 13, ReportStyle.color(m, Ui.FG), Decode.METAR, null);
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
            TextView t;
            if (slot.metar != null) {
                t = words(slot.metar.raw, 12, ReportStyle.color(slot.metar, Ui.FG), Decode.METAR, null);
            } else {
                t = add(Ui.text(this, Ui.hhmm(slot.time) + "  no report", 12, Ui.DIM, true));
            }
            t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        }
    }

    // ------------------------------------------------------------ TAF

    private void tafs(Station s, long now, boolean busy) {
        if (s.wxError != null) {
            warn("Weather update failed: " + s.wxError
                    + (s.tafAt > 0 ? ". Showing data as of " + Ui.dayHm(s.tafAt) + "." : "."));
        }
        Graph graph = Graph.of(s, now);
        if (!graph.isEmpty()) {
            add(Ui.heading(this, "Past 7 days and TAF forecast · UTC"));
            WeekGraphView view = new WeekGraphView(this);
            view.setGraph(graph);
            content.addView(view, Ui.fill());
            TextView key = small("SOLID: OBSERVED · DASHED: TAF FORECAST · DOTTED: TEMPO / PROB · RED BAND: BELOW 500 FT");
            key.setPadding(0, Ui.dp(this, 8), 0, 0);
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
        small("ISSUED " + Ui.dayHm(cur.issued) + " · TAP A WORD TO DECODE IT · RED = LIFR");
        words(TafFormat.lines(cur.raw), 13, Ui.FG, Decode.TAF, cur);

        if (s.tafs.size() > 1) {
            add(Ui.heading(this, "Earlier"));
            for (int i = 1; i < s.tafs.size(); i++) {
                Taf t = s.tafs.get(i);
                if (i > 1) {
                    content.addView(Ui.divider(this));
                }
                TextView label = small("ISSUED " + Ui.dayHm(t.issued));
                label.setPadding(0, Ui.dp(this, i > 1 ? 10 : 0), 0, 0);
                words(TafFormat.lines(t.raw), 12, Ui.DIM, Decode.TAF, t);
            }
        }
    }
}

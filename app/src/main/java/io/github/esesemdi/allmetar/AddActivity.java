package io.github.esesemdi.allmetar;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import io.github.esesemdi.allmetar.core.Airport;
import io.github.esesemdi.allmetar.core.AirportIndex;
import io.github.esesemdi.allmetar.core.Station;
import io.github.esesemdi.allmetar.core.Store;

/** Search the bundled aerodrome list and add a location. */
public final class AddActivity extends Activity {
    private static final int MAX_RESULTS = 40;

    private EditText input;
    private LinearLayout results;
    private volatile List<AirportIndex.Entry> all;
    private boolean adding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        TextView back = Ui.action(this, "‹", v -> finish());
        back.setTextSize(24);
        back.setContentDescription("Back");
        bar.addView(back);
        TextView title = Ui.text(this, "ADD LOCATION", 15, Ui.FG, false);
        title.setLetterSpacing(0.2f);
        bar.addView(title, Ui.weighted());
        root.addView(bar, Ui.fill());
        root.addView(Ui.divider(this));

        input = new EditText(this);
        input.setHint("ICAO code, name or city");
        input.setHintTextColor(Ui.DIM);
        input.setTextColor(Ui.FG);
        input.setTextSize(18);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        root.addView(input, Ui.fill());
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        results = Ui.column(this);
        scroll.addView(results, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search();
            }
        });
        input.requestFocus();

        new Thread(() -> {
            List<AirportIndex.Entry> loaded = null;
            try {
                loaded = App.airports(getApplicationContext());
            } catch (IOException | RuntimeException ignored) {
                // search stays empty; a 4-character code can still be added by hand
            }
            final List<AirportIndex.Entry> done = loaded == null
                    ? new java.util.ArrayList<AirportIndex.Entry>() : loaded;
            App.ui(() -> {
                all = done;
                search();
            });
        }, "allmetar-airports").start();
        search();
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.DIM, false);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        results.addView(t, Ui.fill());
    }

    private void search() {
        results.removeAllViews();
        final String q = input.getText().toString().trim();
        List<AirportIndex.Entry> list = all;
        if (q.isEmpty()) {
            note("Canadian and US aerodromes are built in.\nAny other 4-letter ICAO code can be added for weather only.");
            return;
        }
        if (list == null) {
            note("Loading aerodrome list…");
            return;
        }
        List<AirportIndex.Entry> hits = AirportIndex.search(list, q, MAX_RESULTS);
        final String code = q.toUpperCase(Locale.US);
        boolean exact = false;
        for (final AirportIndex.Entry e : hits) {
            exact |= e.id.equals(code);
            String place = e.place();
            row(e.id, e.name, place.isEmpty() ? e.country : place + " · " + e.country, e.id, e.name);
        }
        if (!exact && code.matches("[A-Z][A-Z0-9]{3}")) {
            row(code, "Not in the built-in list", "Add anyway — weather only, no aerodrome data", code, "");
        }
        if (results.getChildCount() == 0) {
            note("Nothing found.");
        }
    }

    private void row(String id, String name, String detail, final String addId, final String addName) {
        LinearLayout item = Ui.column(this);
        item.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        Ui.ripple(item);
        LinearLayout head = Ui.row(this);
        TextView code = Ui.text(this, id, 15, Ui.FG, true);
        code.setTypeface(code.getTypeface(), android.graphics.Typeface.BOLD);
        head.addView(code, Ui.wrap());
        TextView n = Ui.oneLine(Ui.text(this, name, 14, Ui.FG, false));
        n.setPadding(Ui.dp(this, 12), 0, 0, 0);
        head.addView(n, Ui.weighted());
        item.addView(head, Ui.fill());
        if (!detail.isEmpty()) {
            TextView d = Ui.oneLine(Ui.text(this, detail, 12, Ui.DIM, false));
            d.setPadding(0, Ui.dp(this, 2), 0, 0);
            item.addView(d, Ui.fill());
        }
        item.setOnClickListener(v -> add(addId, addName));
        results.addView(item, Ui.fill());
        results.addView(Ui.divider(this));
    }

    private void add(final String id, final String name) {
        if (adding) {
            return;
        }
        final Store store = App.store(this);
        if (store.get(id) != null) {
            Toast.makeText(this, id + " is already in your list.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (store.isFull()) {
            Toast.makeText(this, "The list holds " + Store.MAX_STATIONS + " locations. Remove one first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!Store.validId(id)) {
            Toast.makeText(this, "That identifier can't be used.", Toast.LENGTH_SHORT).show();
            return;
        }
        adding = true;
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Airport airport = App.airport(app, id);
            String shown = airport != null ? airport.name : name;
            final boolean added = store.add(Station.create(id, shown, airport));
            if (added) {
                App.notifyChanged(app);
                App.refreshStation(app, id);
            }
            App.ui(() -> {
                adding = false;
                if (added) {
                    finish();
                } else {
                    Toast.makeText(app, "Couldn't add " + id + ".", Toast.LENGTH_SHORT).show();
                }
            });
        }, "allmetar-add").start();
    }
}

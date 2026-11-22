package fr.damien.infoneutre;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int C_BG = Color.parseColor("#F2F4F7");
    private static final int C_PRIMARY = Color.parseColor("#1F3A5F");
    private static final int C_TEXT = Color.parseColor("#1B1F24");
    private static final int C_MUTED = Color.parseColor("#5F6B7A");
    private static final int C_CARD = Color.WHITE;

    private static final long HOUR = 3600L * 1000L;
    private static final String[] PERIOD_LABELS = {"24 h", "48 h", "7 jours", "30 jours"};
    private static final long[] PERIOD_MS = {24 * HOUR, 48 * HOUR, 7 * 24 * HOUR, 30 * 24 * HOUR};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    private Store store;
    private Traducteur traducteur;
    private SharedPreferences prefs;

    /** Flux en échec lors du dernier chargement. */
    private final Map<Sources.Feed, String> failures = new LinkedHashMap<>();

    private String currentTheme = Sources.THEMES[0];
    private int periodIndex = 1;
    private boolean loading = false;
    private int loadGeneration = 0;
    private String statusLoad = "";
    private String statusTranslate = "";

    private TextView status;
    private TextView refresh;
    private LinearLayout tabs;
    private LinearLayout periods;
    private LinearLayout list;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_PRIMARY);
        }
        prefs = getSharedPreferences("reglages", MODE_PRIVATE);
        periodIndex = Math.max(0, Math.min(PERIOD_MS.length - 1, prefs.getInt("periode", 1)));

        store = new Store(this);
        store.load();
        try {
            traducteur = new Traducteur();
        } catch (Throwable t) {
            traducteur = null; // services Google absents : affichage en anglais
        }

        setContentView(buildLayout());
        selectTab(currentTheme);
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
        if (traducteur != null) traducteur.close();
        store.save();
    }

    // ------------------------------------------------------------------ Interface

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // En-tête
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(C_PRIMARY);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Info Neutre", 22, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refresh = text("⟳ Actualiser", 15, Color.WHITE, true);
        refresh.setPadding(dp(12), dp(6), dp(12), dp(6));
        refresh.setBackground(rounded(Color.parseColor("#2E5583"), 18));
        refresh.setOnClickListener(v -> reload());
        titleRow.addView(refresh);
        header.addView(titleRow);

        status = text("", 13, Color.parseColor("#CFE0F5"), false);
        status.setPadding(0, dp(4), 0, 0);
        header.addView(status);
        root.addView(header);

        // Onglets (thèmes)
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(C_PRIMARY);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(10), 0, dp(10), dp(10));
        for (String th : Sources.THEMES) {
            TextView tab = text(Sources.label(th), 15, Color.WHITE, true);
            tab.setTag(th);
            tab.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tab.setOnClickListener(v -> selectTab((String) v.getTag()));
            tabs.addView(tab, lp);
        }
        hs.addView(tabs);
        root.addView(hs);

        // Période
        periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        periods.setGravity(Gravity.CENTER_VERTICAL);
        periods.setPadding(dp(14), dp(8), dp(14), dp(2));
        periods.addView(text("Période :", 13, C_MUTED, true));
        for (int i = 0; i < PERIOD_LABELS.length; i++) {
            TextView p = text(PERIOD_LABELS[i], 13, C_PRIMARY, true);
            p.setTag(i);
            p.setPadding(dp(10), dp(5), dp(10), dp(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), 0, 0, 0);
            p.setOnClickListener(v -> selectPeriod((Integer) v.getTag()));
            periods.addView(p, lp);
        }
        root.addView(periods);
        updatePeriodChips();

        // Liste
        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void selectTab(String theme) {
        currentTheme = theme;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean sel = theme.equals(t.getTag());
            t.setBackground(sel ? rounded(Color.WHITE, 18) : rounded(Color.TRANSPARENT, 18));
            t.setTextColor(sel ? C_PRIMARY : Color.WHITE);
        }
        render();
        scroll.scrollTo(0, 0);
    }

    private void selectPeriod(int index) {
        periodIndex = index;
        prefs.edit().putInt("periode", index).apply();
        updatePeriodChips();
        render();
        scroll.scrollTo(0, 0);
    }

    private void updatePeriodChips() {
        for (int i = 1; i < periods.getChildCount(); i++) {
            TextView p = (TextView) periods.getChildAt(i);
            boolean sel = ((Integer) p.getTag()) == periodIndex;
            p.setBackground(sel ? rounded(C_PRIMARY, 14) : rounded(Color.parseColor("#DDE4EE"), 14));
            p.setTextColor(sel ? Color.WHITE : C_PRIMARY);
        }
    }

    private void updateStatus() {
        String s = statusLoad;
        if (!statusTranslate.isEmpty()) s = s.isEmpty() ? statusTranslate : s + "\n" + statusTranslate;
        status.setText(s);
    }

    private void render() {
        list.removeAllViews();
        long now = System.currentTimeMillis();
        long window = PERIOD_MS[periodIndex];
        List<Article> items = Synthese.build(currentTheme, store.all(), now, window);

        // Résumé du thème (comptage factuel)
        Set<String> srcs = new LinkedHashSet<>();
        for (Article a : items) {
            srcs.add(a.source);
            srcs.addAll(a.alsoSources);
        }
        String head;
        if (items.isEmpty()) {
            head = loading ? "Chargement…" : "Aucun sujet sur la période choisie.";
        } else {
            head = items.size() + " sujet" + (items.size() > 1 ? "s" : "")
                    + " · " + PERIOD_LABELS[periodIndex] + " · " + join(srcs);
        }
        TextView h = text(head, 14, C_MUTED, true);
        h.setPadding(dp(6), dp(2), dp(6), dp(8));
        list.addView(h);

        for (Article a : items) list.addView(card(a, now));

        // Sources indisponibles pour ce thème
        List<String> ko = new ArrayList<>();
        for (Map.Entry<Sources.Feed, String> e : failures.entrySet()) {
            if (e.getKey().theme.equals(currentTheme)) {
                ko.add(e.getKey().source + " (" + e.getValue() + ")");
            }
        }
        if (!ko.isEmpty()) {
            TextView f = text("Flux indisponible : " + join(ko), 12, C_MUTED, false);
            f.setPadding(dp(6), dp(12), dp(6), 0);
            list.addView(f);
        }

        String note = "Titres et chapôs reproduits tels que publiés par les sources, sans "
                + "réécriture ni commentaire. Les sources anglophones sont traduites "
                + "automatiquement (traduction littérale) ; « Voir l'original » affiche le texte "
                + "d'origine. Les sujets traités par plusieurs médias sont regroupés.";
        if (periodIndex >= 2) {
            note += " Au-delà de 48 h, l'affichage s'appuie sur l'historique conservé par "
                    + "l'appli : il se remplit à chaque ouverture (30 jours maximum).";
        }
        TextView n = text(note, 12, C_MUTED, false);
        n.setPadding(dp(6), dp(16), dp(6), 0);
        list.addView(n);
    }

    private View card(Article a, long now) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(C_CARD, 12));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        if (android.os.Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));

        StringBuilder meta = new StringBuilder(a.source);
        if (a.isTranslated()) meta.append(" · traduit de l'anglais");
        else if ("en".equals(a.lang)) meta.append(" · en anglais");
        if (a.timeMillis > 0) meta.append(" · ").append(formatTime(a.timeMillis, now));
        c.addView(text(meta.toString(), 12, C_PRIMARY, true));

        final TextView t = text(a.displayTitle(), 17, C_TEXT, true);
        t.setPadding(0, dp(4), 0, 0);
        c.addView(t);

        final TextView s = text("", 14, C_TEXT, false);
        s.setPadding(0, dp(6), 0, 0);
        s.setMaxLines(6);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        setSummary(s, a.displaySummary(), a.displayTitle());
        c.addView(s);

        if (a.isTranslated()) {
            final TextView toggle = text("Voir l'original", 13, C_MUTED, true);
            toggle.setPadding(0, dp(8), 0, 0);
            final boolean[] original = {false};
            toggle.setOnClickListener(v -> {
                original[0] = !original[0];
                if (original[0]) {
                    t.setText(a.title);
                    setSummary(s, a.summary, a.title);
                    toggle.setText("Voir la traduction");
                } else {
                    t.setText(a.displayTitle());
                    setSummary(s, a.displaySummary(), a.displayTitle());
                    toggle.setText("Voir l'original");
                }
            });
            c.addView(toggle);
        }

        if (!a.alsoSources.isEmpty()) {
            TextView lbl = text("Aussi chez :", 13, C_MUTED, false);
            lbl.setPadding(0, dp(8), 0, dp(2));
            c.addView(lbl);
            for (int i = 0; i < a.alsoSources.size(); i++) {
                final String url = a.alsoLinks.get(i);
                TextView o = text("↗ " + a.alsoSources.get(i), 13, C_PRIMARY, true);
                o.setPadding(dp(8), dp(3), 0, dp(3));
                o.setOnClickListener(v -> open(url));
                c.addView(o);
            }
        }

        c.setOnClickListener(v -> open(a.link));
        return c;
    }

    private void setSummary(TextView s, String summary, String title) {
        boolean show = summary != null && !summary.isEmpty() && !summary.equals(title);
        s.setText(show ? summary : "");
        s.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ Chargement

    private void reload() {
        if (loading) return;
        loading = true;
        final int gen = ++loadGeneration;
        refresh.setAlpha(0.5f);
        failures.clear();

        final List<Sources.Feed> feeds = Sources.all();
        final int[] done = {0};
        final List<Article> fresh = new ArrayList<>();
        statusLoad = "Chargement de " + feeds.size() + " flux…";
        updateStatus();
        render();

        for (final Sources.Feed f : feeds) {
            pool.execute(() -> {
                List<Article> got = null;
                String err = null;
                try {
                    got = FeedFetcher.fetch(f);
                } catch (Exception e) {
                    err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    if (err.length() > 60) err = err.substring(0, 60);
                }
                final List<Article> result = got;
                final String error = err;
                // Tout l'état est modifié sur le fil principal.
                ui.post(() -> {
                    if (gen != loadGeneration || isFinishing()) return;
                    if (result != null) fresh.addAll(result);
                    else failures.put(f, error);
                    done[0]++;
                    if (done[0] < feeds.size()) {
                        statusLoad = "Chargement… " + done[0] + "/" + feeds.size() + " flux";
                        updateStatus();
                    } else {
                        onAllFeedsDone(fresh, feeds.size());
                    }
                });
            });
        }
    }

    private void onAllFeedsDone(List<Article> fresh, int total) {
        long now = System.currentTimeMillis();
        int added = store.merge(fresh, now);
        store.prune(now);
        store.save();

        loading = false;
        refresh.setAlpha(1f);
        int ok = total - failures.size();
        statusLoad = "Mis à jour à " + new SimpleDateFormat("HH:mm", Locale.FRANCE)
                .format(new Date(now)) + " · " + ok + "/" + total + " flux · "
                + added + " nouveau" + (added > 1 ? "x" : "");
        updateStatus();
        if (ok == 0) {
            Toast.makeText(this, "Aucun flux reçu : vérifie la connexion.", Toast.LENGTH_LONG).show();
        }
        render();
        startTranslation();
    }

    // ------------------------------------------------------------------ Traduction

    private void startTranslation() {
        if (traducteur == null || traducteur.isRunning()) return;
        List<Article> todo = new ArrayList<>();
        for (Article a : store.all()) if (a.needsTranslation()) todo.add(a);
        if (todo.isEmpty()) return;
        // Les plus récents d'abord.
        Collections.sort(todo, (x, y) -> Long.compare(y.timeMillis, x.timeMillis));

        statusTranslate = "Traduction de " + todo.size() + " articles anglophones…";
        updateStatus();
        traducteur.translate(todo, new Traducteur.Listener() {
            @Override
            public void onProgress(int done, int total) {
                statusTranslate = "Traduction… " + done + "/" + total;
                updateStatus();
                if (done % 30 == 0) render();
            }

            @Override
            public void onFinished(boolean ok, String error) {
                if (isFinishing()) return;
                statusTranslate = ok ? "" : "Traduction indisponible (modèle non téléchargé)"
                        + " : textes anglais affichés tels quels.";
                updateStatus();
                store.save();
                render();
            }
        });
    }

    // ------------------------------------------------------------------ Utilitaires

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir le lien", Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatTime(long t, long now) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(t);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);
        boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        String pattern = today ? "'aujourd''hui' HH:mm" : "EEE d MMM HH:mm";
        return new SimpleDateFormat(pattern, Locale.FRANCE).format(new Date(t));
    }

    private static String join(Iterable<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}

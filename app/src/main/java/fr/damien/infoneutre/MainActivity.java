package fr.damien.infoneutre;

import android.app.Activity;
import android.content.Intent;
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
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private static final int C_BG = Color.parseColor("#F2F4F7");
    private static final int C_PRIMARY = Color.parseColor("#1F3A5F");
    private static final int C_TEXT = Color.parseColor("#1B1F24");
    private static final int C_MUTED = Color.parseColor("#5F6B7A");
    private static final int C_CARD = Color.WHITE;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    private final List<Article> articles = Collections.synchronizedList(new ArrayList<>());
    /** Flux en échec : URL -> message. */
    private final Map<Sources.Feed, String> failures =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private String currentTheme = Sources.THEMES[0];
    private boolean loading = false;
    private long lastUpdate = 0;

    private TextView status;
    private TextView refresh;
    private LinearLayout tabs;
    private LinearLayout list;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_PRIMARY);
        }
        setContentView(buildLayout());
        selectTab(currentTheme);
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
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

        // Onglets
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

        // Liste
        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(10), dp(10), dp(24));
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

    private void render() {
        list.removeAllViews();
        long now = System.currentTimeMillis();
        List<Article> snapshot;
        synchronized (articles) {
            snapshot = new ArrayList<>(articles);
        }
        List<Article> items = Synthese.build(currentTheme, snapshot, now);

        // Résumé du thème (comptage factuel)
        Set<String> srcs = new LinkedHashSet<>();
        for (Article a : items) {
            srcs.add(a.source);
            srcs.addAll(a.alsoSources);
        }
        String head;
        if (items.isEmpty()) {
            head = loading ? "Chargement…" : "Aucun sujet sur les dernières 48 h.";
        } else {
            head = items.size() + " sujet" + (items.size() > 1 ? "s" : "")
                    + " · 48 dernières heures · " + join(srcs);
        }
        TextView h = text(head, 14, C_MUTED, true);
        h.setPadding(dp(6), dp(2), dp(6), dp(8));
        list.addView(h);

        for (Article a : items) list.addView(card(a, now));

        // Sources indisponibles pour ce thème
        List<String> ko = new ArrayList<>();
        synchronized (failures) {
            for (Map.Entry<Sources.Feed, String> e : failures.entrySet()) {
                if (e.getKey().theme.equals(currentTheme)) {
                    ko.add(e.getKey().source + " (" + e.getValue() + ")");
                }
            }
        }
        if (!ko.isEmpty()) {
            TextView f = text("Flux indisponible : " + join(ko), 12, C_MUTED, false);
            f.setPadding(dp(6), dp(12), dp(6), 0);
            list.addView(f);
        }

        TextView note = text("Titres et chapôs reproduits tels que publiés par les sources, "
                + "sans réécriture ni commentaire. Les sujets traités par plusieurs médias "
                + "sont regroupés.", 12, C_MUTED, false);
        note.setPadding(dp(6), dp(16), dp(6), 0);
        list.addView(note);
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

        String meta = a.source + (a.timeMillis > 0 ? " · " + formatTime(a.timeMillis, now) : "");
        TextView m = text(meta, 12, C_PRIMARY, true);
        c.addView(m);

        TextView t = text(a.title, 17, C_TEXT, true);
        t.setPadding(0, dp(4), 0, 0);
        c.addView(t);

        if (!a.summary.isEmpty() && !a.summary.equals(a.title)) {
            TextView s = text(a.summary, 14, C_TEXT, false);
            s.setPadding(0, dp(6), 0, 0);
            s.setMaxLines(6);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(s);
        }

        if (!a.alsoSources.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);
            row.addView(text("Aussi chez : ", 13, C_MUTED, false));
            for (int i = 0; i < a.alsoSources.size(); i++) {
                final String url = a.alsoLinks.get(i);
                TextView s = text(a.alsoSources.get(i) + (i < a.alsoSources.size() - 1 ? ", " : ""),
                        13, C_PRIMARY, true);
                s.setOnClickListener(v -> open(url));
                row.addView(s);
            }
            c.addView(row);
        }

        c.setOnClickListener(v -> open(a.link));
        return c;
    }

    // ------------------------------------------------------------------ Chargement

    private void reload() {
        if (loading) return;
        loading = true;
        refresh.setAlpha(0.5f);
        articles.clear();
        failures.clear();

        final List<Sources.Feed> feeds = Sources.all();
        final AtomicInteger done = new AtomicInteger(0);
        status.setText("Chargement de " + feeds.size() + " flux…");
        render();

        for (final Sources.Feed f : feeds) {
            pool.execute(() -> {
                try {
                    List<Article> got = FeedFetcher.fetch(f);
                    articles.addAll(got);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    if (msg.length() > 60) msg = msg.substring(0, 60);
                    failures.put(f, msg);
                }
                final int n = done.incrementAndGet();
                ui.post(() -> onFeedDone(n, feeds.size()));
            });
        }
    }

    private void onFeedDone(int n, int total) {
        if (isFinishing()) return;
        if (n < total) {
            status.setText("Chargement… " + n + "/" + total + " flux");
            return;
        }
        loading = false;
        refresh.setAlpha(1f);
        lastUpdate = System.currentTimeMillis();
        int ok = total - failures.size();
        status.setText("Mis à jour à " + new SimpleDateFormat("HH:mm", Locale.FRANCE)
                .format(new Date(lastUpdate)) + " · " + ok + "/" + total + " flux reçus");
        if (ok == 0) {
            Toast.makeText(this, "Aucun flux reçu : vérifie la connexion.", Toast.LENGTH_LONG).show();
        }
        render();
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

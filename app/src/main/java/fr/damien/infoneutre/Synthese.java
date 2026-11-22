package fr.damien.infoneutre;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Synthèse purement mécanique : tri chronologique, fenêtre de temps,
 * fusion des sujets publiés par plusieurs sources. Aucun texte n'est généré.
 */
public final class Synthese {

    /** Fenêtre conservée : 48 h. */
    public static final long WINDOW_MS = 48L * 3600L * 1000L;
    /** Nombre maximum de sujets affichés par thème. */
    public static final int MAX_PER_THEME = 60;
    /** Seuil de similarité des titres pour considérer deux articles comme le même sujet. */
    private static final double SIMILARITY = 0.6;

    private static final Set<String> STOP = new HashSet<>();
    static {
        String[] w = ("le la les un une des de du d l au aux et ou en a à dans sur pour par "
                + "avec sans ce cet cette ces son sa ses leur leurs qui que quoi est sont "
                + "été être ont il elle ils elles on se ne pas plus apres après avant entre "
                + "contre comme mais the of to in").split(" ");
        Collections.addAll(STOP, w);
    }

    public static List<Article> build(String theme, List<Article> all, long now) {
        List<Article> list = new ArrayList<>();
        for (Article a : all) {
            if (!theme.equals(a.theme)) continue;
            if (a.timeMillis > 0 && now - a.timeMillis > WINDOW_MS) continue;
            list.add(a);
        }
        // Plus récent en premier ; dates inconnues à la fin.
        Collections.sort(list, (x, y) -> Long.compare(y.timeMillis, x.timeMillis));

        List<Article> result = new ArrayList<>();
        List<Set<String>> keys = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();

        for (Article a : list) {
            if (!seenLinks.add(a.link)) continue; // même article dans deux flux
            Set<String> k = words(a.title);
            int match = -1;
            for (int i = 0; i < result.size(); i++) {
                if (similarity(k, keys.get(i)) >= SIMILARITY) { match = i; break; }
            }
            if (match >= 0) {
                Article main = result.get(match);
                if (!main.source.equals(a.source) && !main.alsoSources.contains(a.source)) {
                    main.alsoSources.add(a.source);
                    main.alsoLinks.add(a.link);
                }
            } else if (result.size() < MAX_PER_THEME) {
                a.alsoSources.clear();
                a.alsoLinks.clear();
                result.add(a);
                keys.add(k);
            }
        }
        return result;
    }

    static Set<String> words(String title) {
        String n = Normalizer.normalize(title.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ");
        Set<String> s = new HashSet<>();
        for (String w : n.split(" ")) {
            if (w.length() > 2 && !STOP.contains(w)) s.add(w);
        }
        return s;
    }

    /** Coefficient de recouvrement : intersection / plus petit ensemble. */
    static double similarity(Set<String> a, Set<String> b) {
        if (a.size() < 3 || b.size() < 3) return 0;
        int inter = 0;
        for (String w : a) if (b.contains(w)) inter++;
        return inter / (double) Math.min(a.size(), b.size());
    }

    private Synthese() {}
}

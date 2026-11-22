package fr.damien.infoneutre;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Thème saisi par l'utilisateur : un nom et des mots-clés. */
public final class ThemePerso {

    private static final String PREF_KEY = "themes_perso";

    public final String name;
    public final List<String> keywords;
    /** Thème local : recherche limitée à la presse locale (Gironde). */
    public final boolean local;

    public ThemePerso(String name, List<String> keywords) {
        this(name, keywords, false);
    }

    public ThemePerso(String name, List<String> keywords, boolean local) {
        this.name = name;
        this.keywords = keywords;
        this.local = local;
    }

    /** « UBB, Bordeaux-Bègles » -> nom « UBB », mots-clés [UBB, Bordeaux-Bègles]. */
    public static ThemePerso parse(String input) {
        List<String> kw = new ArrayList<>();
        for (String p : input.split("[,;]")) {
            String t = p.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty() && !kw.contains(t)) kw.add(t);
        }
        if (kw.isEmpty()) return null;
        return new ThemePerso(kw.get(0), kw);
    }

    /** Vrai si l'un des mots-clés apparaît (mot entier, sans tenir compte des accents/majuscules). */
    public boolean matches(Article a) {
        String hay = " " + norm(a.title) + " " + norm(a.summary) + " "
                + norm(a.titleFr) + " " + norm(a.summaryFr) + " ";
        for (String k : keywords) {
            String n = norm(k).trim();
            if (!n.isEmpty() && hay.contains(" " + n + " ")) return true;
        }
        return false;
    }

    static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ");
    }

    // ---------------------------------------------------------------- Flux associés

    /** Sites interrogés en français (mêmes médias que les onglets, + presse régionale de référence). */
    private static final String[] SITES_FR = {
            "francetvinfo.fr", "la1ere.franceinfo.fr", "france24.com", "rfi.fr", "ouest-france.fr", "sudouest.fr",
            "france3-regions.franceinfo.fr", "francebleu.fr",
            "lequipe.fr", "rtbf.be", "radio-canada.ca", "futura-sciences.com"
    };
    /** Sites interrogés en anglais (traduits). */
    private static final String[] SITES_EN = {
            "reuters.com", "apnews.com", "bbc.co.uk", "bbc.com", "npr.org", "dw.com"
    };

    /** Presse locale de référence (thème « Autour de moi »). */
    private static final String[] SITES_LOCAL = {
            "sudouest.fr", "france3-regions.franceinfo.fr", "francebleu.fr"
    };

    /** Taille max des groupes : Google ignore la fin des requêtes trop longues. */
    private static final int CHUNK = 4;

    public List<Sources.Feed> feeds() {
        List<Sources.Feed> l = new ArrayList<>();
        if (local) {
            addFeeds(l, SITES_LOCAL, "fr");
        } else {
            addFeeds(l, SITES_FR, "fr");
            addFeeds(l, SITES_EN, "en");
        }
        return l;
    }

    private void addFeeds(List<Sources.Feed> l, String[] sites, String lang) {
        for (int k = 0; k < keywords.size(); k += CHUNK) {
            List<String> kw = keywords.subList(k, Math.min(keywords.size(), k + CHUNK));
            for (int i = 0; i < sites.length; i += CHUNK) {
                List<String> st = new ArrayList<>();
                for (int j = i; j < Math.min(sites.length, i + CHUNK); j++) st.add(sites[j]);
                l.add(Sources.googleSearch(name, query(kw, st), lang));
            }
        }
    }

    private static String query(List<String> kws, List<String> sites) {
        StringBuilder q = new StringBuilder("(");
        for (int i = 0; i < kws.size(); i++) {
            if (i > 0) q.append(" OR ");
            String k = kws.get(i).replace("\"", "");
            q.append(k.contains(" ") || k.contains("-") || k.contains("'") ? "\"" + k + "\"" : k);
        }
        q.append(") (");
        for (int i = 0; i < sites.size(); i++) {
            if (i > 0) q.append(" OR ");
            q.append("site:").append(sites.get(i));
        }
        q.append(") when:7d");
        return q.toString();
    }

    /** Thème créé au premier lancement : la commune et ses voisines. */
    public static ThemePerso autourDeMoi() {
        List<String> kw = new ArrayList<>();
        String[] communes = {"Saint-Médard-en-Jalles", "Le Haillan", "Saint-Aubin-de-Médoc",
                "Le Taillan-Médoc", "Martignas-sur-Jalle", "Saint-Jean-d'Illac", "Eysines",
                "Mérignac", "Le Porge", "Salaunes"};
        for (String c : communes) kw.add(c);
        return new ThemePerso("Autour de moi", kw, true);
    }

    // ---------------------------------------------------------------- Sauvegarde

    public static List<ThemePerso> load(SharedPreferences prefs) {
        List<ThemePerso> l = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(PREF_KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray k = o.getJSONArray("k");
                List<String> kw = new ArrayList<>();
                for (int j = 0; j < k.length(); j++) kw.add(k.getString(j));
                l.add(new ThemePerso(o.getString("n"), kw, o.optBoolean("loc", false)));
            }
        } catch (Exception ignored) {
            // préférences illisibles : aucun thème perso
        }
        return l;
    }

    public static void save(SharedPreferences prefs, List<ThemePerso> themes) {
        try {
            JSONArray arr = new JSONArray();
            for (ThemePerso t : themes) {
                JSONObject o = new JSONObject();
                o.put("n", t.name);
                JSONArray k = new JSONArray();
                for (String s : t.keywords) k.put(s);
                o.put("k", k);
                o.put("loc", t.local);
                arr.put(o);
            }
            prefs.edit().putString(PREF_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
            // rien
        }
    }
}

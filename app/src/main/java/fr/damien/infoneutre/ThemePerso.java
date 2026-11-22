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

    public ThemePerso(String name, List<String> keywords) {
        this.name = name;
        this.keywords = keywords;
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
            "lequipe.fr", "rtbf.be", "radio-canada.ca", "futura-sciences.com"
    };
    /** Sites interrogés en anglais (traduits). */
    private static final String[] SITES_EN = {
            "reuters.com", "apnews.com", "bbc.co.uk", "bbc.com", "npr.org", "dw.com"
    };

    public List<Sources.Feed> feeds() {
        List<Sources.Feed> l = new ArrayList<>();
        l.add(Sources.googleSearch(name, query(SITES_FR), "fr"));
        l.add(Sources.googleSearch(name, query(SITES_EN), "en"));
        return l;
    }

    private String query(String[] sites) {
        StringBuilder q = new StringBuilder("(");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) q.append(" OR ");
            String k = keywords.get(i).replace("\"", "");
            q.append(k.contains(" ") || k.contains("-") ? "\"" + k + "\"" : k);
        }
        q.append(") (");
        for (int i = 0; i < sites.length; i++) {
            if (i > 0) q.append(" OR ");
            q.append("site:").append(sites[i]);
        }
        q.append(") when:7d");
        return q.toString();
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
                l.add(new ThemePerso(o.getString("n"), kw));
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
                arr.put(o);
            }
            prefs.edit().putString(PREF_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
            // rien
        }
    }
}

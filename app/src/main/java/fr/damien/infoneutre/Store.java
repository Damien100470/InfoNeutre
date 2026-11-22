package fr.damien.infoneutre;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Historique local des articles (fichier JSON dans le stockage privé de l'appli).
 * Permet d'afficher plus que ce que contiennent les flux (souvent 1 à 2 jours)
 * et de garder les traductions déjà faites.
 */
public final class Store {

    /** Durée de conservation maximale. */
    public static final long KEEP_MS = 31L * 24L * 3600L * 1000L;

    private static final String FILE = "articles.json";

    /** Clé = thème + lien (un même lien peut apparaître dans deux thèmes). */
    private final Map<String, Article> map = new LinkedHashMap<>();
    private final File file;

    public Store(Context ctx) {
        file = new File(ctx.getFilesDir(), FILE);
    }

    private static String key(Article a) {
        return a.theme + "|" + a.link;
    }

    public List<Article> all() {
        return new ArrayList<>(map.values());
    }

    /** Ajoute les nouveaux articles ; conserve ceux déjà connus (et leur traduction). */
    public int merge(List<Article> fresh, long now) {
        int added = 0;
        for (Article a : fresh) {
            String k = key(a);
            if (map.containsKey(k)) continue;
            if (a.timeMillis <= 0 || a.timeMillis > now + 3600_000L) a.timeMillis = now;
            map.put(k, a);
            added++;
        }
        return added;
    }

    public void prune(long now) {
        List<String> old = new ArrayList<>();
        for (Map.Entry<String, Article> e : map.entrySet()) {
            if (now - e.getValue().timeMillis > KEEP_MS) old.add(e.getKey());
        }
        for (String k : old) map.remove(k);
    }

    public void load() {
        map.clear();
        if (!file.exists()) return;
        try {
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            JSONArray arr = new JSONArray(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Article a = new Article(o.getString("th"), o.getString("src"), o.getString("t"),
                        o.optString("s", ""), o.getString("l"), o.optLong("d", 0),
                        o.optString("lang", "fr"));
                if (o.has("tf")) a.titleFr = o.getString("tf");
                if (o.has("sf")) a.summaryFr = o.getString("sf");
                map.put(key(a), a);
            }
        } catch (Exception e) {
            map.clear(); // fichier corrompu : on repart de zéro
        }
    }

    public void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Article a : map.values()) {
                JSONObject o = new JSONObject();
                o.put("th", a.theme);
                o.put("src", a.source);
                o.put("t", a.title);
                o.put("s", a.summary);
                o.put("l", a.link);
                o.put("d", a.timeMillis);
                o.put("lang", a.lang);
                if (a.titleFr != null) o.put("tf", a.titleFr);
                if (a.summaryFr != null) o.put("sf", a.summaryFr);
                arr.put(o);
            }
            File tmp = new File(file.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        } catch (Exception ignored) {
            // l'historique est un confort : une erreur d'écriture n'empêche pas l'affichage
        }
    }
}

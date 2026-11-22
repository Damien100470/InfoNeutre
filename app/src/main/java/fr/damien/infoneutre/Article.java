package fr.damien.infoneutre;

import java.util.ArrayList;
import java.util.List;

/** Une information telle que publiée par la source (aucune réécriture). */
public final class Article {
    public final String theme;
    public final String source;
    public final String title;
    public final String summary;   // chapô fourni par la source, texte brut
    public final String link;
    public final long timeMillis;  // 0 si inconnu

    /** Autres sources ayant publié le même sujet (fusion des doublons). */
    public final List<String> alsoSources = new ArrayList<>();
    public final List<String> alsoLinks = new ArrayList<>();

    public Article(String theme, String source, String title, String summary,
                   String link, long timeMillis) {
        this.theme = theme;
        this.source = source;
        this.title = title;
        this.summary = summary;
        this.link = link;
        this.timeMillis = timeMillis;
    }
}

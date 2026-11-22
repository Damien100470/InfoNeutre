package fr.damien.infoneutre;

import java.util.ArrayList;
import java.util.List;

/** Une information telle que publiée par la source (aucune réécriture). */
public final class Article {
    public final String theme;
    public final String source;
    public final String title;     // titre original
    public final String summary;   // chapô original, texte brut
    public final String link;
    public final String lang;      // "fr" ou "en"
    public long timeMillis;        // date de publication (ou de 1re réception si inconnue)

    /** Traduction automatique (null tant qu'elle n'est pas faite). */
    public String titleFr;
    public String summaryFr;

    /** Autres sources ayant publié le même sujet (fusion des doublons, recalculée à l'affichage). */
    public final List<String> alsoSources = new ArrayList<>();
    public final List<String> alsoLinks = new ArrayList<>();

    public Article(String theme, String source, String title, String summary,
                   String link, long timeMillis, String lang) {
        this.theme = theme;
        this.source = source;
        this.title = title;
        this.summary = summary;
        this.link = link;
        this.timeMillis = timeMillis;
        this.lang = lang;
    }

    public boolean needsTranslation() {
        return "en".equals(lang) && titleFr == null;
    }

    public boolean isTranslated() {
        return "en".equals(lang) && titleFr != null;
    }

    public String displayTitle() {
        return titleFr != null ? titleFr : title;
    }

    public String displaySummary() {
        if (titleFr != null && summaryFr != null) return summaryFr;
        return summary;
    }
}

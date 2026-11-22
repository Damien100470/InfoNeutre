package fr.damien.infoneutre;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste des flux RSS utilisés, classés par thème.
 * Pour ajouter / retirer une source : modifier uniquement ce fichier.
 *
 * Choix éditorial : médias d'information reconnus (agences de presse,
 * audiovisuel public, presse de référence), pas de presse people.
 * Les sources en anglais (lang = "en") sont traduites en français sur le téléphone.
 */
public final class Sources {

    public static final String INTERNATIONAL = "International";
    public static final String SPATIAL = "Spatial";
    public static final String NATIONAL = "National";
    public static final String GIRONDE = "Gironde";
    public static final String RUGBY = "Rugby";
    public static final String TENNIS = "Tennis";
    public static final String FOOTBALL = "Football";

    /** Ordre d'affichage des onglets. */
    public static final String[] THEMES = {
            INTERNATIONAL, SPATIAL, NATIONAL, GIRONDE, RUGBY, TENNIS, FOOTBALL
    };

    /** Libellé affiché sur l'onglet. */
    public static String label(String theme) {
        if (RUGBY.equals(theme) || TENNIS.equals(theme) || FOOTBALL.equals(theme)) {
            return "Sport · " + theme;
        }
        return theme;
    }

    public static final class Feed {
        public final String theme;
        public final String source;
        public final String url;
        /** "fr" ou "en". */
        public final String lang;
        /**
         * Flux de recherche Google Actualités (pour Reuters, qui n'a plus de flux RSS) :
         * le titre se termine par « - Reuters » et le résumé ne contient que le titre.
         */
        public final boolean googleNews;
        /** Nom du média lu dans chaque article (recherches multi-sites). */
        public final boolean itemSource;

        Feed(String theme, String source, String url, String lang, boolean googleNews) {
            this(theme, source, url, lang, googleNews, false);
        }

        Feed(String theme, String source, String url, String lang, boolean googleNews,
             boolean itemSource) {
            this.theme = theme;
            this.source = source;
            this.url = url;
            this.lang = lang;
            this.googleNews = googleNews;
            this.itemSource = itemSource;
        }
    }

    private static Feed fr(String theme, String source, String url) {
        return new Feed(theme, source, url, "fr", false);
    }

    private static Feed en(String theme, String source, String url) {
        return new Feed(theme, source, url, "en", false);
    }

    /** Articles Reuters d'une rubrique (chemin d'URL), via Google Actualités, 7 derniers jours. */
    private static Feed reuters(String theme, String path) {
        String url = "https://news.google.com/rss/search?q=when:7d+site:reuters.com/" + path
                + "&hl=en-US&gl=US&ceid=US:en";
        return new Feed(theme, "Reuters", url, "en", true);
    }

    /**
     * Recherche Google Actualités (thème saisi par l'utilisateur). Le nom du média est lu
     * dans chaque article (balise source).
     */
    public static Feed googleSearch(String theme, String query, String lang) {
        String loc = "fr".equals(lang) ? "&hl=fr&gl=FR&ceid=FR:fr" : "&hl=en-US&gl=US&ceid=US:en";
        String q;
        try {
            q = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            q = query.replace(" ", "+");
        }
        String url = "https://news.google.com/rss/search?q=" + q + loc;
        return new Feed(theme, "fr".equals(lang) ? "Recherche (FR)" : "Recherche (EN)", url, lang,
                true, true);
    }

    /** Un site sans flux RSS, lu via Google Actualités (7 derniers jours). */
    private static Feed site(String theme, String source, String domain) {
        String url = "https://news.google.com/rss/search?q=when:7d+site:" + domain
                + "&hl=fr&gl=FR&ceid=FR:fr";
        return new Feed(theme, source, url, "fr", true);
    }

    public static List<Feed> all() {
        List<Feed> l = new ArrayList<>();

        // ---- International
        l.add(fr(INTERNATIONAL, "franceinfo", "https://www.francetvinfo.fr/monde.rss"));
        l.add(fr(INTERNATIONAL, "France 24", "https://www.france24.com/fr/europe/rss"));
        l.add(fr(INTERNATIONAL, "France 24", "https://www.france24.com/fr/afrique/rss"));
        l.add(fr(INTERNATIONAL, "RTBF (Belgique)",
                "https://rss.rtbf.be/article/rss/highlight_rtbfinfo_info-monde.xml?source=internal"));
        l.add(fr(INTERNATIONAL, "RTBF (Belgique)",
                "https://rss.rtbf.be/article/rss/highlight_rtbfinfo_monde-europe.xml?source=internal"));
        l.add(fr(INTERNATIONAL, "Radio-Canada", "https://ici.radio-canada.ca/rss/4159"));
        l.add(reuters(INTERNATIONAL, "world"));
        l.add(en(INTERNATIONAL, "BBC", "https://feeds.bbci.co.uk/news/world/rss.xml"));
        l.add(en(INTERNATIONAL, "NPR", "https://feeds.npr.org/1004/rss.xml"));
        l.add(en(INTERNATIONAL, "DW", "https://rss.dw.com/xml/rss-en-all"));

        // ---- Spatial
        l.add(fr(SPATIAL, "franceinfo", "https://www.francetvinfo.fr/sciences/espace.rss"));
        l.add(fr(SPATIAL, "Futura", "https://www.futura-sciences.com/rss/espace/actualites.xml"));
        l.add(en(SPATIAL, "NASA", "https://www.nasa.gov/news-release/feed/"));
        l.add(en(SPATIAL, "ESA", "https://www.esa.int/rssfeed/TopNews"));
        l.add(en(SPATIAL, "Space.com", "https://www.space.com/feeds/news"));

        // ---- National
        l.add(fr(NATIONAL, "franceinfo", "https://www.francetvinfo.fr/france.rss"));
        l.add(fr(NATIONAL, "franceinfo", "https://www.francetvinfo.fr/politique.rss"));
        l.add(fr(NATIONAL, "France 24", "https://www.france24.com/fr/france/rss"));
        l.add(fr(NATIONAL, "Ouest-France", "https://www.ouest-france.fr/rss-en-continu.xml"));
        l.add(site(NATIONAL, "Outre-mer la 1ère", "la1ere.franceinfo.fr"));

        // ---- Gironde (presse locale de référence + audiovisuel public régional)
        l.add(fr(GIRONDE, "Sud Ouest", "https://www.sudouest.fr/gironde/rss.xml"));
        l.add(site(GIRONDE, "France 3 Aquitaine",
                "france3-regions.franceinfo.fr/nouvelle-aquitaine/gironde"));
        l.add(googleSearch(GIRONDE, "(Gironde OR Bordeaux OR Médoc OR Arcachon OR Libourne) "
                + "(site:sudouest.fr OR site:francebleu.fr OR site:france3-regions.franceinfo.fr) "
                + "when:7d", "fr"));

        // ---- Sport
        l.add(fr(RUGBY, "franceinfo", "https://www.francetvinfo.fr/sports/rugby.rss"));
        l.add(fr(RUGBY, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Rugby/"));
        l.add(en(RUGBY, "BBC Sport", "https://feeds.bbci.co.uk/sport/rugby-union/rss.xml"));
        l.add(reuters(RUGBY, "sports/rugby"));

        l.add(fr(TENNIS, "franceinfo", "https://www.francetvinfo.fr/sports/tennis.rss"));
        l.add(fr(TENNIS, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Tennis/"));
        l.add(en(TENNIS, "BBC Sport", "https://feeds.bbci.co.uk/sport/tennis/rss.xml"));
        l.add(reuters(TENNIS, "sports/tennis"));

        l.add(fr(FOOTBALL, "franceinfo", "https://www.francetvinfo.fr/sports/foot.rss"));
        l.add(fr(FOOTBALL, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Football/"));
        l.add(en(FOOTBALL, "BBC Sport", "https://feeds.bbci.co.uk/sport/football/rss.xml"));
        l.add(reuters(FOOTBALL, "sports/soccer"));

        return l;
    }

    private Sources() {}
}

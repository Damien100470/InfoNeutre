package fr.damien.infoneutre;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste des flux RSS utilisés, classés par thème.
 * Pour ajouter / retirer une source : modifier uniquement ce fichier.
 *
 * Choix éditorial : médias d'information généralistes ou spécialisés
 * reconnus (service public, agences, presse de référence), pas de presse people.
 */
public final class Sources {

    public static final String INTERNATIONAL = "International";
    public static final String SPATIAL = "Spatial";
    public static final String NATIONAL = "National";
    public static final String RUGBY = "Rugby";
    public static final String TENNIS = "Tennis";
    public static final String FOOTBALL = "Football";

    /** Ordre d'affichage des onglets. */
    public static final String[] THEMES = {
            INTERNATIONAL, SPATIAL, NATIONAL, RUGBY, TENNIS, FOOTBALL
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

        Feed(String theme, String source, String url) {
            this.theme = theme;
            this.source = source;
            this.url = url;
        }
    }

    public static List<Feed> all() {
        List<Feed> l = new ArrayList<>();

        // ---- International
        l.add(new Feed(INTERNATIONAL, "franceinfo", "https://www.francetvinfo.fr/monde.rss"));
        l.add(new Feed(INTERNATIONAL, "France 24", "https://www.france24.com/fr/europe/rss"));
        l.add(new Feed(INTERNATIONAL, "France 24", "https://www.france24.com/fr/afrique/rss"));
        // Médias publics étrangers
        l.add(new Feed(INTERNATIONAL, "RTBF (Belgique)",
                "https://rss.rtbf.be/article/rss/highlight_rtbfinfo_info-monde.xml?source=internal"));
        l.add(new Feed(INTERNATIONAL, "RTBF (Belgique)",
                "https://rss.rtbf.be/article/rss/highlight_rtbfinfo_monde-europe.xml?source=internal"));
        l.add(new Feed(INTERNATIONAL, "Radio-Canada", "https://ici.radio-canada.ca/rss/4159"));
        l.add(new Feed(INTERNATIONAL, "BBC (EN)", "https://feeds.bbci.co.uk/news/world/rss.xml"));
        l.add(new Feed(INTERNATIONAL, "DW (EN)", "https://rss.dw.com/xml/rss-en-all"));

        // ---- Spatial
        l.add(new Feed(SPATIAL, "franceinfo", "https://www.francetvinfo.fr/sciences/espace.rss"));
        l.add(new Feed(SPATIAL, "Futura", "https://www.futura-sciences.com/rss/espace/actualites.xml"));
        l.add(new Feed(SPATIAL, "NASA (EN)", "https://www.nasa.gov/news-release/feed/"));

        // ---- National
        l.add(new Feed(NATIONAL, "franceinfo", "https://www.francetvinfo.fr/france.rss"));
        l.add(new Feed(NATIONAL, "franceinfo", "https://www.francetvinfo.fr/politique.rss"));
        l.add(new Feed(NATIONAL, "France 24", "https://www.france24.com/fr/france/rss"));
        l.add(new Feed(NATIONAL, "Ouest-France", "https://www.ouest-france.fr/rss-en-continu.xml"));

        // ---- Sport
        l.add(new Feed(RUGBY, "franceinfo", "https://www.francetvinfo.fr/sports/rugby.rss"));
        l.add(new Feed(RUGBY, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Rugby/"));
        l.add(new Feed(TENNIS, "franceinfo", "https://www.francetvinfo.fr/sports/tennis.rss"));
        l.add(new Feed(TENNIS, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Tennis/"));
        l.add(new Feed(FOOTBALL, "franceinfo", "https://www.francetvinfo.fr/sports/foot.rss"));
        l.add(new Feed(FOOTBALL, "L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/Football/"));

        return l;
    }

    private Sources() {}
}

package fr.damien.infoneutre;

import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Télécharge un flux RSS 2.0 ou Atom et en extrait les articles, sans modifier le texte. */
public final class FeedFetcher {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;

    public static List<Article> fetch(Sources.Feed feed) throws Exception {
        HttpURLConnection conn = open(feed.url);
        try {
            InputStream in = new BufferedInputStream(conn.getInputStream());
            try {
                return parse(in, feed);
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Ouvre la connexion en suivant aussi les redirections http <-> https. */
    private static HttpURLConnection open(String address) throws Exception {
        String current = address;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) InfoNeutre/1.0");
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("Redirection sans adresse");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            return c;
        }
        throw new Exception("Trop de redirections");
    }

    private static List<Article> parse(InputStream in, Sources.Feed feed) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, null); // encodage lu dans la déclaration XML

        List<Article> out = new ArrayList<>();
        boolean inItem = false;
        String title = null, link = null, desc = null, date = null;

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String name = localName(p.getName());
                if (name.equals("item") || name.equals("entry")) {
                    inItem = true;
                    title = link = desc = date = null;
                } else if (inItem) {
                    switch (name) {
                        case "title":
                            title = readText(p);
                            break;
                        case "link": {
                            String href = p.getAttributeValue(null, "href");
                            String rel = p.getAttributeValue(null, "rel");
                            if (href != null) {
                                if (link == null || "alternate".equals(rel)) link = href;
                            } else {
                                String t = readText(p);
                                if (t != null && !t.trim().isEmpty()) link = t.trim();
                            }
                            break;
                        }
                        case "description":
                        case "summary":
                            if (desc == null) desc = readText(p);
                            break;
                        case "pubDate":
                        case "published":
                        case "updated":
                        case "date": // dc:date
                            if (date == null) date = readText(p);
                            break;
                        default:
                            break;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String name = localName(p.getName());
                if (inItem && (name.equals("item") || name.equals("entry"))) {
                    inItem = false;
                    String t = clean(title);
                    String d = clean(desc);
                    if (feed.googleNews) {
                        // « Titre - Reuters » -> « Titre » ; le résumé Google ne répète que le titre.
                        int cut = t.lastIndexOf(" - ");
                        if (cut > 0) t = t.substring(0, cut).trim();
                        d = "";
                    }
                    if (!t.isEmpty() && link != null) {
                        out.add(new Article(feed.theme, feed.source, t, d,
                                link.trim(), parseDate(date), feed.lang));
                    }
                }
            }
            ev = p.next();
        }
        return out;
    }

    private static String localName(String n) {
        if (n == null) return "";
        int i = n.indexOf(':');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    /** Lit le texte d'un élément (y compris CDATA) et se place sur sa balise fermante. */
    private static String readText(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.TEXT) {
                sb.append(p.getText());
            } else if (ev == XmlPullParser.START_TAG) {
                depth++;
            } else if (ev == XmlPullParser.END_TAG) {
                depth--;
            } else if (ev == XmlPullParser.END_DOCUMENT) {
                break;
            }
        }
        return sb.toString();
    }

    /** Retire le HTML et les espaces superflus. Le texte lui-même n'est pas modifié. */
    @SuppressWarnings("deprecation")
    static String clean(String s) {
        if (s == null) return "";
        String txt;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            txt = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            txt = Html.fromHtml(s).toString();
        }
        txt = txt.replace('￼', ' ').replace(' ', ' ');
        return txt.replaceAll("\\s+", " ").trim();
    }

    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
    };

    static long parseDate(String s) {
        if (s == null) return 0;
        String v = s.trim();
        for (String f : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.ENGLISH);
                if (f.endsWith("'Z'")) sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                ParsePosition pos = new ParsePosition(0);
                java.util.Date d = sdf.parse(v, pos);
                if (d != null && pos.getIndex() > 0) return d.getTime();
            } catch (Exception ignored) {
                // format suivant
            }
        }
        return 0;
    }

    private FeedFetcher() {}
}

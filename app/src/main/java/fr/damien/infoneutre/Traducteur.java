package fr.damien.infoneutre;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Traduction anglais -> français sur le téléphone (Google ML Kit, gratuit, hors ligne
 * une fois le modèle téléchargé, ~30 Mo la première fois). Traduction littérale
 * automatique : pas de résumé ni de reformulation.
 * Toutes les méthodes s'utilisent depuis le fil principal.
 */
public final class Traducteur {

    public interface Listener {
        /** Appelé régulièrement pendant la traduction. */
        void onProgress(int done, int total);
        /** Appelé à la fin (ok = false si le modèle n'a pas pu être téléchargé). */
        void onFinished(boolean ok, String error);
    }

    private static final int MAX_SUMMARY_CHARS = 1000;

    private final Translator translator;
    private final ArrayDeque<Article> queue = new ArrayDeque<>();
    private Listener listener;
    private boolean running = false;
    private boolean closed = false;
    private int done, total;

    public Traducteur() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.FRENCH)
                .build();
        translator = Translation.getClient(options);
    }

    public boolean isRunning() {
        return running;
    }

    /** Traduit les articles donnés (ceux déjà traduits sont ignorés). */
    public void translate(List<Article> articles, Listener l) {
        if (running || closed) return;
        queue.clear();
        for (Article a : articles) if (a.needsTranslation()) queue.add(a);
        if (queue.isEmpty()) {
            l.onFinished(true, null);
            return;
        }
        listener = l;
        running = true;
        done = 0;
        total = queue.size();
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> next())
                .addOnFailureListener(e -> finish(false, e.getMessage()));
    }

    private void next() {
        if (closed) return;
        final Article a = queue.poll();
        if (a == null) {
            finish(true, null);
            return;
        }
        translator.translate(a.title)
                .addOnSuccessListener(t -> {
                    String s = a.summary;
                    if (s == null || s.isEmpty() || s.equals(a.title)) {
                        a.summaryFr = "";
                        a.titleFr = t;
                        step();
                        return;
                    }
                    if (s.length() > MAX_SUMMARY_CHARS) s = s.substring(0, MAX_SUMMARY_CHARS) + "…";
                    translator.translate(s)
                            .addOnSuccessListener(st -> {
                                a.summaryFr = st;
                                a.titleFr = t;
                                step();
                            })
                            .addOnFailureListener(e -> {
                                a.summaryFr = null; // chapô affiché en anglais
                                a.titleFr = t;
                                step();
                            });
                })
                .addOnFailureListener(e -> step()); // on passe à l'article suivant
    }

    private void step() {
        done++;
        if (listener != null && (done % 10 == 0 || done == total)) listener.onProgress(done, total);
        next();
    }

    private void finish(boolean ok, String err) {
        running = false;
        queue.clear();
        if (listener != null && !closed) listener.onFinished(ok, err);
    }

    public void close() {
        closed = true;
        translator.close();
    }
}

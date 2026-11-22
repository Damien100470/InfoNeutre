# Info Neutre — application Android

Agrégateur d'actualités **sans interprétation** : l'appli lit les flux RSS de médias
d'information reconnus et affiche, pour chaque sujet, le **titre et le chapô tels que
publiés par la source** (aucune réécriture, aucune IA, aucun commentaire).

## Thèmes (onglets)

| Onglet            | Sources                                               |
|-------------------|-------------------------------------------------------|
| International     | franceinfo, France 24, RTBF, Radio-Canada · Reuters, BBC, NPR, DW (traduits) |
| Spatial           | franceinfo, Futura · NASA, ESA, Space.com (traduits) |
| National          | franceinfo (France, Politique), France 24 (France), Ouest-France, Outre-mer la 1ère |
| Sport · Rugby     | franceinfo, L'Équipe · BBC Sport, Reuters (traduits) |
| Sport · Tennis    | franceinfo, L'Équipe · BBC Sport, Reuters (traduits) |
| Sport · Football  | franceinfo, L'Équipe · BBC Sport, Reuters (traduits) |

Traduction anglais → français faite sur le téléphone (Google ML Kit, gratuit ;
modèle d'environ 30 Mo téléchargé au premier lancement). « Voir l'original » sur
chaque carte traduite. Reuters n'ayant plus de flux RSS, ses articles sont lus
via la recherche Google Actualités (rubriques Monde et Sport).

## Thèmes personnels

Onglet **＋ Thème** : saisir un ou plusieurs mots-clés séparés par des virgules
(ex. `UBB, Bordeaux-Bègles`). L'onglet créé (★) regroupe :
- les articles déjà reçus, tous thèmes confondus, qui contiennent ces mots ;
- une recherche Google Actualités limitée aux sites des médias retenus
  (franceinfo, France 24, RFI, Ouest-France, Sud Ouest, L'Équipe, RTBF, Radio-Canada,
  Futura ; Reuters, AP, BBC, NPR, DW traduits), sur 7 jours.

Appui long sur un onglet ★ pour le supprimer.

## Ce que fait la « synthèse » (purement mécanique)

- période au choix : 24 h, 48 h, 7 jours, 30 jours (historique local conservé 30 jours) ;
- tri du plus récent au plus ancien ;
- regroupe un même sujet publié par plusieurs médias (titres très proches) :
  une seule carte, avec « Aussi chez : … » cliquable ;
- retire le HTML des chapôs, sans toucher au texte ;
- indique en bas d'onglet les flux qui n'ont pas répondu.

Toucher une carte ouvre l'article d'origine dans le navigateur.

## Obtenir l'APK

### Option A — GitHub (sans rien installer)
1. Crée un dépôt GitHub et envoie-y tout le dossier (y compris `.github/`).
2. Onglet **Actions** → le workflow « Compiler l'APK » se lance tout seul (≈ 3-5 min).
3. Ouvre l'exécution terminée → section **Artifacts** → télécharge `InfoNeutre-apk`
   (un zip contenant `app-debug.apk`).
4. Sur le téléphone : ouvre l'APK, autorise « installer des applis inconnues » pour
   le gestionnaire de fichiers / navigateur utilisé.

### Option B — Android Studio
1. *File › Open* → choisir le dossier `InfoNeutre`.
2. Laisser la synchronisation Gradle se terminer.
3. *Build › Build App Bundle(s) / APK(s) › Build APK(s)*,
   ou brancher le téléphone (débogage USB) et cliquer ▶.

## Modifier les sources

Tout est dans `app/src/main/java/fr/damien/infoneutre/Sources.java` :
une ligne `l.add(new Feed(THEME, "Nom", "URL du flux RSS"));` par flux.

## Structure

```
app/src/main/java/fr/damien/infoneutre/
  Sources.java      liste des flux par thème
  FeedFetcher.java  téléchargement + lecture RSS/Atom
  Synthese.java     tri, fenêtre 48 h, fusion des doublons
  Article.java      modèle de données
  MainActivity.java interface (onglets, cartes)
```

Java, minSdk 24 (Android 7+), aucune bibliothèque externe.

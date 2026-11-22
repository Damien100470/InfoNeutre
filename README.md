# Info Neutre — application Android

Agrégateur d'actualités **sans interprétation** : l'appli lit les flux RSS de médias
d'information reconnus et affiche, pour chaque sujet, le **titre et le chapô tels que
publiés par la source** (aucune réécriture, aucune IA, aucun commentaire).

## Thèmes (onglets)

| Onglet            | Sources                                               |
|-------------------|-------------------------------------------------------|
| International     | franceinfo (Monde), France 24 (Europe, Afrique)       |
| Spatial           | franceinfo (Espace), Futura (Espace)                  |
| National          | franceinfo (France, Politique), France 24 (France), Ouest-France |
| Sport · Rugby     | franceinfo, L'Équipe                                  |
| Sport · Tennis    | franceinfo, L'Équipe                                  |
| Sport · Football  | franceinfo, L'Équipe                                  |

## Ce que fait la « synthèse » (purement mécanique)

- garde les 48 dernières heures, du plus récent au plus ancien ;
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

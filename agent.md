# Agent PhotosGestion

Document de travail pour garantir un dev propre: code robuste et optimise, journaux d erreurs clairs, tests systematiques, UX simple pour des personnes peu a l aise avec l informatique.

## Objectif produit
- Permettre d importer, trier, rechercher et consulter des photos facilement.
- Priorite a la simplicite: moins d options, plus de guides et d actions claires.
- Eviter toute perte de donnees: pas de suppression sans confirmation/annulation.

## Stack proposee
- Java 24, Maven.
- UI: JavaFX + FXML (controls accessibles, gros boutons, polices lisibles).
- Logging: SLF4J + Logback (fichiers rotates, niveau configurable).
- Tests: JUnit 5, Mockito; couverture avec JaCoCo; rapport Surefire.
- Qualite: Checkstyle ou Spotless + formatage coherent; Git hooks pre-commit (facultatif).

## Architecture cible (packages)
- `core`: model (Photo, Album, Tag, Rating), services (ScanService, MetadataService, SearchService, AlbumService, ExportService).
- `infra`: adaptateurs systeme (FileSystemGateway, ImageLoader, ThumbnailCache, ExifReader), persistance locale (index sur disque).
- `ui`: scenes/controleurs JavaFX (Accueil, Parcours, Detail, Edition tags/albums), theming.
- `app`: bootstrap, configuration, wiring (factory simple ou DI leger).

## UX pour publics novices
- Parcours guide: bouton Importer visible, messages en langage simple (sans jargon).
- Gros elements cliquables, contraste eleve, texte min 14-16px, curseur explicite.
- Actions critiques avec confirmation et option Annuler.
- Filtres simples: par date, lieu, tag, favori; recherche texte avec suggestions.
- Vue principale en grille de miniatures; double-clic ou Entree ouvre le detail.
- Feedback immediat: barres de progression pour scans, etat connecte/deconnecte du disque.

## Journalisation et gestion des erreurs
- Toujours passer par un logger de classe: `private static final Logger log = LoggerFactory.getLogger(X.class);`
- Loguer:
  - `info`: evenements fonctionnels (import termine, export cree).
  - `warn`: cas degradables (fichier corrompu, exif manquant, lecture partielle).
  - `error`: echec definitif (disque inaccessible, ecriture impossible) avec contexte (chemin, action) et stacktrace.
- Ne jamais afficher de stacktrace brute a l utilisateur; montrer un message clair et action suggerer (reessayer, verifier le disque).
- Config Logback (exemple a integrer) :
  - Fichier `logs/app.log`, rotation quotidienne ou taille limite (ex: 10MB, 5 archives).
  - Pattern concis: date, niveau, thread, classe, message.
  - Niveau par defaut `INFO`, activable en `DEBUG` via prop JVM.
- Ajouter des garde-fous: validation des chemins, existence/permissions, tests d espace disque avant ecriture.

## Performances et robustesse
- Chargement paresseux des miniatures; cache en memoire + disque; redimensionnement en tache de fond.
- Scan des repertoires en tache asynchrone; UI non bloquante; barre de progression.
- Debounce sur watchers de fichiers pour limiter le bruit lors de copies massives.
- Gestion des doublons (hash ou taille+timestamp) et affichage clair pour les resoudre.
- Toujours liberer les ressources (try-with-resources) et fermer les streams.

## Tests (minimum a viser)
- Unitaires core: logique de tri, filtrage, recherche, detection doublons, calcul tags/rating.
- Services infra: lecture EXIF (mock I/O), generation de miniatures (dimensions attendues), gestion d erreurs I/O.
- UI: tests de controleurs (avec TestFX ou mocks) pour les flux critiques (import, suppression, ajout tag).
- Contrats: interfaces de repository/adaptateurs avec tests de reference pour toute implementation.
- Couverture cible: >80% sur core; integration smoke sur parcours complet (import -> affichage -> export).
- Nomenclature: `shouldDoX_whenY`; un test par cas; donnees stables via fixtures.

## Roadmap initiale
1) Mettre a jour `pom.xml` avec: JavaFX, SLF4J+Logback, JUnit 5, Mockito, JaCoCo, Checkstyle/Spotless.
2) Poser l arborescence de packages `core/infra/ui/app` et des classes model de base (`Photo`, `Album`, `Tag`, `Rating`).
3) Ajouter une config Logback par defaut (`src/main/resources/logback.xml`) et un logger dans `Main`.
4) Implenter un premier vertical slice: scan d un dossier -> index en memoire -> affichage grille simple.
5) Ecrire les tests unitaires du slice (services + adaptateur FS) + un test UI smoke.
6) Ajouter un jeu d echantillons (quelques images de test) dans `src/test/resources`.
7) Brancher JaCoCo et faire tourner `mvn test` en CI locale.

## Exemple de logger et handling
```java
private static final Logger log = LoggerFactory.getLogger(ScanService.class);

public List<Photo> scan(Path root) {
    if (root == null || !Files.isDirectory(root)) {
        log.warn("Scan ignore: chemin invalide {}", root);
        return List.of();
    }
    try {
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .map(this::toPhoto)
                .flatMap(Optional::stream)
                .toList();
    } catch (IOException e) {
        log.error("Echec de scan du dossier {}", root, e);
        throw new ScanException("Impossible de parcourir " + root, e);
    }
}
```

## Commandes Maven utiles
- `mvn test` : tests unitaires.
- `mvn -DskipTests package` : build rapide.
- `mvn verify` : tests + couverture JaCoCo (une fois ajoute).

## Definition de fini pour une tache
- Code lisible, formate, noms clairs; pas d avertissement compilateur.
- Logs aux bons niveaux et messages exploitables.
- Tests unitaires passes, cas limites couverts; pas de TODO bloqueur.
- UX coherente et simple; messages utilisateurs clairs; pas de crash silencieux.

# GestionPhoto

Ce projet JavaFX permet de tester rapidement l'interface et les services de gestion de photos. Le build Maven est configure pour appliquer le style de code, mesurer la couverture et gerer les profils JavaFX par OS.

## Commandes utiles
- `mvn spotless:apply` : applique le formatage Google Java Format sur les sources et le `pom.xml`.
- `mvn verify` : execute les tests, genere le rapport de couverture JaCoCo et bloque en cas de violation Spotless.
- `mvn javafx:run` : lance l'application JavaFX en utilisant le profil detecte automatiquement (linux, windows, mac x64 ou aarch64).

Les profils JavaFX definis dans le `pom.xml` restent en place pour choisir automatiquement le classifier selon l'OS. Aucune configuration supplementaire n'est necessaire pour `mvn verify`, le plugin JavaFX est independant des taches de verification.

## Si tu obtiens `UnsupportedClassVersionError`
Cela veut dire que Maven tourne avec un JDK trop vieux (ex: 1.8). Le projet et JavaFX 21 exigent Java 21+.

Exemple pour forcer Maven a utiliser Java 21 sous PowerShell :
```
$env:JAVA_HOME="C:\Program Files\Java\jdk-24"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Pwindows -DskipTests javafx:run
```

## Lancement local sans erreur JavaFX
- Utilise un JDK 21+ (mets a jour `JAVA_HOME` si besoin).
- Sur Windows, `.\run-windows.ps1` construit le module-path JavaFX depuis le cache Maven et lance `org.example.app.PhotoGestionApp`. Ajoute tes arguments applicatifs a la suite si necessaire.
- Si des jars JavaFX manquent, le script demandera d'executer `mvn -Pwindows dependency:resolve` pour les telecharger.
- En IDE, ajoute les options VM `--module-path <chemin_des_jars_javafx>-win.jar` et `--add-modules javafx.controls,javafx.fxml` si tu preferes conserver une configuration Application standard.

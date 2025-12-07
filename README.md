# GestionPhoto

Ce projet JavaFX permet de tester rapidement l'interface et les services de gestion de photos. Le build Maven est configuré pour faire respecter le style de code, mesurer la couverture et rester compatible avec les profils JavaFX existants.

## Commandes utiles
- `mvn spotless:apply` : applique le formatage partagé Google Java Format sur les sources et le `pom.xml`.
- `mvn verify` : exécute les tests, génère le rapport de couverture JaCoCo et bloque en cas de violation Spotless.
- `mvn javafx:run` : lance l'application JavaFX en utilisant le profil détecté automatiquement (linux, windows, mac x64 ou aarch64).

Les profils JavaFX définis dans le `pom.xml` restent inchangés pour sélectionner le classifier approprié selon l'OS. Aucune configuration supplémentaire n'est nécessaire pour `mvn verify`, car le plugin JavaFX est indépendant des tâches de vérification.

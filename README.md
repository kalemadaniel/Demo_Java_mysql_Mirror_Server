# Demo_Java_mysql_Mirror_Server

Application de bureau **Java Swing** permettant de synchroniser les données de tables sélectionnées entre deux bases **MySQL**, dans les deux sens.

La version **Swing 1.1** fonctionne avec **Java 8**, **NetBeans 8.0.2** et **Ant**, sans Maven. Le pilote MySQL est inclus dans le projet.

> Cette application synchronise les données de tables préparées. Elle ne réalise pas une réplication complète des serveurs MySQL.

## Aperçu

<img width="883" height="757" alt="Capture d&#39;écran 2026-11-05 145254" src="https://github.com/user-attachments/assets/8e2fdb48-ac92-4539-8e49-f8ccab9b3e30" />

## Fonctionnalités

- Configuration de deux serveurs MySQL : A et B.
- Test de connexion pour chaque serveur.
- Synchronisation automatique bidirectionnelle.
- Intervalle de synchronisation configurable.
- Barre de progression.
- Mise en veille, reprise et désactivation.
- Journal d’activité.
- Mode démonstration sans connexion MySQL.
- Enregistrement des paramètres sans sauvegarde des mots de passe.

## États de synchronisation

| État | Description |
| --- | --- |
| Non activé | La synchronisation est désactivée. |
| En cours | Un cycle de synchronisation est en cours. |
| En attente | L’application attend le prochain cycle. |
| En veille | La synchronisation est suspendue. |
| Erreur | Une erreur est survenue ; consulter le journal. |

## Prérequis

- **Java 8 ou ultérieur** pour lancer l’application.
- **JDK 8 ou ultérieur** pour compiler les sources.
- **NetBeans 8.0.2 avec Ant**, ou Ant disponible dans le terminal.
- Deux bases **MySQL 8.0 ou ultérieures** pour la synchronisation réelle.

Le pilote **MySQL Connector/J 9.7.0** est fourni dans le dossier `lib`.

## Installation

1. Télécharger et extraire entièrement le projet.
2. Placer le dossier, par exemple, dans :

   ```text
   D:\projets\Demo_Java_mysql_Mirror_Server_Swing
   ```

3. Conserver les dossiers `src`, `lib`, `nbproject` et `dist`.

## Lancer sans NetBeans

Double-cliquer sur :

```text
Lancer.bat
```

Ou exécuter depuis la racine du projet :

```bat
java -jar dist\Demo_Java_mysql_Mirror_Server.jar
```

Le dossier `dist/lib` doit rester à côté du fichier JAR.

## Ouvrir dans NetBeans

1. Fermer l’ancien projet Maven s’il est ouvert.
2. Sélectionner **Fichier → Ouvrir un projet**.
3. Choisir le dossier `Demo_Java_mysql_Mirror_Server_Swing`.
4. Vérifier que ce dossier contient `build.xml` et `nbproject`.
5. Faire un clic droit sur le projet → **Définir comme projet principal**.
6. Cliquer sur **Exécuter**, ou appuyer sur **F6**.

> Le journal doit afficher `Buildfile: …\build.xml`.
> S’il affiche `mvn.bat`, NetBeans lance encore l’ancien projet Maven.

## Compiler

### Sous Windows

```bat
Compiler.bat
```

Le script utilise `JAVA_HOME` ou le JDK installé dans :

```text
C:\Program Files\Java\jdk1.8.0_92
```

### Avec Ant

Depuis la racine du projet :

```sh
ant jar
ant test
ant run
```

Le fichier exécutable est généré dans `dist`.

Les tests Swing nécessitent un environnement graphique.

## Mode démonstration

Le mode démonstration est activé au démarrage.

1. Laisser **Mode démonstration** coché.
2. Cliquer sur **Activer**.
3. Observer la progression et le passage en attente.
4. Essayer **Mettre en veille**, **Reprendre** et **Désactiver**.

Ce mode ne synchronise aucune donnée réelle.

**Attention :** les boutons **Tester la connexion** contactent réellement les serveurs renseignés, même en mode démonstration.

## Configuration MySQL

### Préparer les bases

Créer les bases sur les deux serveurs, puis exécuter le script suivant dans chacune :

```text
sql/exemple.sql
```

Le compte utilisé doit disposer des droits :

- `SELECT`
- `INSERT`
- `UPDATE`

L’application ne crée pas automatiquement les bases, les comptes ou les tables.

### Structure des tables

Les tables doivent :

- Exister sur les deux bases avec la même structure.
- Utiliser le moteur **InnoDB**.
- Posséder une clé primaire simple.
- Contenir `updated_at TIMESTAMP(6) NOT NULL`.
- Contenir `is_deleted TINYINT NOT NULL`.

Exemple :

```sql
CREATE TABLE contacts (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nom VARCHAR(160) NOT NULL,
    email VARCHAR(254),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
```

Utiliser des UUID pour éviter les collisions d’identifiants et synchroniser les horloges des deux serveurs.

### Activer la synchronisation réelle

1. Désactiver le moteur.
2. Décocher **Mode démonstration**.
3. Configurer A et B :
   - Hôte ou adresse IP.
   - Port MySQL.
   - Base de données.
   - Utilisateur.
   - Mot de passe.
   - Mode TLS.
4. Tester les deux connexions.
5. Indiquer les tables séparées par des virgules.
6. Choisir l’intervalle.
7. Cliquer sur **Activer**.

L’application doit rester ouverte pour effectuer les cycles.

## Sécurité des connexions

| Mode TLS | Description |
| --- | --- |
| `VERIFY_IDENTITY` | Chiffrement et vérification du certificat et du nom du serveur. Mode par défaut. |
| `REQUIRED` | Chiffrement sans vérification de l’identité du serveur. |
| `DISABLED` | Connexion sans chiffrement, réservée à un essai local isolé. |

Avec `VERIFY_IDENTITY`, le certificat du serveur doit être reconnu par Java.

## Règles de synchronisation

- Une ligne absente d’un côté est copiée depuis l’autre base.
- En cas de différence, la ligne avec le `updated_at` le plus récent gagne.
- Si les dates sont identiques mais les valeurs différentes, le serveur **A** est prioritaire.
- Les conflits à dates identiques sont signalés dans le journal.
- Une modification concurrente détectée sur la cible est reportée au prochain cycle.
- Il n’y a pas de fusion champ par champ.

### Suppressions logiques

Pour propager une suppression :

```sql
UPDATE contacts
SET is_deleted = 1
WHERE id = 'UUID_A_REMPLACER';
```

Pour afficher les lignes actives :

```sql
SELECT *
FROM contacts
WHERE is_deleted = 0;
```

> Ne pas utiliser `DELETE` pour une suppression à synchroniser.
> Une ligne physiquement supprimée peut être recopiée depuis l’autre serveur.
> Conserver les lignes marquées comme supprimées.

### Arrêt et reprise

Chaque écriture est validée séparément.

Une interruption peut laisser un cycle partiellement effectué. Les cycles suivants recommencent la comparaison.

La mise en veille et la désactivation n’annulent pas les écritures déjà validées.

## Limites

- Maximum de **10 000 lignes par table et par serveur**.
- Lecture complète des tables à chaque cycle.
- Pas de copie des schémas, utilisateurs, vues ou procédures.
- Pas de clés primaires composées.
- Pas de colonnes calculées, BLOB, types binaires, BIT, JSON ou géométriques.
- Les contraintes uniques et les clés étrangères restent actives.
- Privilégier les tables indépendantes, sans déclencheurs métier.
- Utiliser une seule instance de synchronisation pour une même paire de bases.
- Aucun service Windows n’est installé.

## Paramètres et journal

Les paramètres sont enregistrés sur demande dans :

```text
%USERPROFILE%\.Demo_Java_mysql_Mirror_Server.properties
```

Les mots de passe ne sont jamais sauvegardés.

Le journal reste en mémoire et disparaît à la fermeture. Les états de connexion correspondent au dernier test ou cycle.

Le compteur d’écritures totalise uniquement les cycles achevés normalement.

## Structure du projet

```text
Demo_Java_mysql_Mirror_Server_Swing/
├── src/
│   └── mirror/
│       ├── App.java
│       └── SyncEngine.java
├── test/
│   └── mirror/
│       ├── EngineTest.java
│       └── UiTest.java
├── nbproject/
│   └── project.xml
├── lib/
├── dist/
├── sql/
│   └── exemple.sql
├── build.xml
├── Lancer.bat
├── Compiler.bat
├── MANIFEST.MF
├── Apercu.png
├── VERIFICATION.txt
├── LICENCE-PILOTE.txt
└── README.md
```

## Vérifications effectuées

- Compilation réussie avec **JDK 1.8.0_92**.
- Compilation Ant réussie avec les outils de **NetBeans 8.0.2**.
- **14 assertions du moteur réussies**.
- **13 assertions Swing réussies**.
- Tests Swing également exécutés depuis le JAR livré.
- Chargement du pilote MySQL vérifié.
- Configuration XML du projet validée pour NetBeans 8.0.2.

**La synchronisation réelle entre deux serveurs reste à valider.**
Les tests transactionnels utilisent un double JDBC.

Avant d’utiliser des données importantes, tester les insertions, modifications, suppressions logiques, conflits et reprises après coupure sur deux bases d’essai.

## Dépannage

| Problème | Solution |
| --- | --- |
| Erreur Maven `peer not authenticated` | Ouvrir le nouveau projet Swing contenant `build.xml`. |
| Java introuvable | Vérifier Java et la variable `JAVA_HOME`. |
| Pilote MySQL absent | Extraire entièrement l’archive et conserver les dossiers `lib`. |
| Connexion refusée | Vérifier l’hôte, le port, le service MySQL, les identifiants et les droits. |
| Certificat non reconnu | Vérifier la chaîne de confiance Java et le nom du serveur. |
| Structure incompatible | Vérifier la structure des tables et les colonnes obligatoires. |
| Ligne supprimée qui réapparaît | Utiliser une suppression logique avec `is_deleted = 1`. |

## Auteur

**Développeur Kalema Daniel Jonathan**

📧 [kalemadaniel2014@gmail.com](mailto:kalemadaniel2014@gmail.com)

## Licence

Aucune licence de diffusion n’est encore définie pour le code de l’application.

Le pilote MySQL Connector/J conserve sa propre licence. Consulter
[LICENCE-PILOTE.txt](LICENCE-PILOTE.txt) et les notices présentes dans le JAR.

# LoL Results

Application de saisie et consultation de résultats League of Legends esport (LEC, LCS, LCK, LPL, LCP, CBLOL). Backend Java / Spring Boot, frontend Vue 3, base PostgreSQL.

Ce README couvre tout ce qu'il faut lancer **sur ta machine** : je n'ai pas d'accès terminal chez toi, donc les commandes ci-dessous sont à copier-coller toi-même (dans le terminal intégré d'IntelliJ, par exemple).

## 1. Prérequis

- **Java 21** (JDK)
- **Maven** (ou utilise le wrapper `mvnw` si tu en ajoutes un plus tard — ici on utilise `mvn` directement)
- **Node.js 20+** et npm
- **PostgreSQL 14+**
- **Git**

## 2. Principe du projet

- Les **matchs** (`match`) sont la seule source de vérité : un match peut exister avant d'être joué (`SCHEDULED`, scores vides) puis être complété (`COMPLETED`).
- Le **classement** et le **tête-à-tête** ne sont **pas stockés** : ils sont recalculés à chaque requête à partir des matchs joués (`StandingsService` côté backend). Comme ça, pas de risque d'incohérence entre les résultats et le classement, contrairement au fichier Excel maintenu à la main.
- Les **playoffs** sont pour l'instant de simples matchs avec un `roundLabel` libre ("QF", "SF", "F", "Losers Bracket R1"...), pas un arbre de bracket graphique. C'est un choix volontaire pour la v1, évoqué comme piste d'évolution plus bas.

## 3. Base de données

```bash
# Se connecter à Postgres (adapte selon ton install) et créer l'utilisateur + la base
psql -U postgres
```

```sql
CREATE USER lol_user WITH PASSWORD 'lol_password';
CREATE DATABASE lol_results OWNER lol_user;
\q
```

Ces identifiants correspondent aux valeurs par défaut de `backend/src/main/resources/application.yml` (surchargeable via les variables d'environnement `DB_USER` / `DB_PASSWORD`). Change le mot de passe si tu veux, mais pense à l'aligner avec `application.yml` ou tes variables d'environnement.

## 4. Git

```bash
cd lol-results
git init
git add .
git commit -m "Scaffold initial : backend Spring Boot, frontend Vue 3, import Excel"
```

Tu pourras ajouter un remote (GitHub/GitLab) plus tard avec `git remote add origin <url>` quand tu voudras héberger le repo.

## 5. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

Au démarrage, **Flyway applique automatiquement les migrations** dans `src/main/resources/db/migration/` :
- `V1__init.sql` : création du schéma (equipes, competitions, groupes, matchs)
- `V2__seed_data.sql` : import des données de ton fichier Excel (généré, voir section 7)

L'API démarre sur `http://localhost:8080`. Endpoints principaux :

| Méthode | URL | Description |
|---|---|---|
| GET | `/api/competitions` | Liste des compétitions |
| POST | `/api/competitions` | Créer une compétition |
| GET | `/api/teams` | Liste des équipes |
| POST | `/api/teams` | Créer une équipe |
| GET | `/api/matches?competitionId=1` | Matchs d'une compétition |
| POST | `/api/matches` | Créer un match |
| PUT | `/api/matches/{id}` | Modifier un match (ex: renseigner un score) |
| GET | `/api/standings?competitionId=1` | Classement recalculé |
| GET | `/api/head-to-head?competitionId=1` | Matrice tête-à-tête recalculée |

**Dans IntelliJ (Community)** : `File → Open` sur `backend/pom.xml` (ouvrir comme projet). IntelliJ Community détecte Maven nativement. Comme il n'y a pas d'icône "run Spring Boot" dédiée en Community, deux options : lancer `mvn spring-boot:run` dans le terminal intégré (le plus simple), ou créer une configuration de lancement "Application" pointant sur `com.charles.lolresults.LolResultsApplication`.

⚠️ **Je n'ai pas pu compiler ce backend dans mon bac à sable** (accès à Maven Central bloqué par la politique réseau de cet environnement) — j'ai relu le code à la main et validé le schéma + les données importées directement en SQL (`psql`), mais le tout premier `mvn spring-boot:run` chez toi est le vrai test. Si une erreur de compilation apparaît, copie-la-moi et je corrige.

## 6. Frontend (Vue 3)

```bash
cd frontend
npm install
npm run dev
```

Le frontend démarre sur `http://localhost:5173` et appelle l'API sur `http://localhost:8080` (CORS déjà autorisé côté backend pour ce port, voir `WebConfig.java`).

Pages disponibles : liste des compétitions (`/`), détail d'une compétition avec calendrier/résultats éditables + classement (`/competitions/:id`), gestion des équipes (`/teams`).

**Dans IntelliJ Community** : le support JavaScript/Vue de base fonctionne pour éditer les fichiers, mais l'auto-complétion Vue avancée est surtout un atout d'Ultimate. Ça n'empêche pas de développer : édite dans IntelliJ, lance `npm run dev` dans un terminal à côté.

## 7. Importer les données de ton Excel

Le script lit uniquement l'onglet **"Feuil1"** (le calendrier à plat) — c'est la seule feuille structurée de façon exploitable automatiquement. Les feuilles par ligue (LCK, LPL...) sont une mise en forme visuelle à la main (matrice + bracket positionné librement), pas des données. Les onglets MSI / Esports World Cup ne sont pas dans Feuil1 et ne sont donc pas importés.

```bash
cd import
pip install -r requirements.txt
python3 import_excel.py /chemin/vers/LOL_2026.xlsx > ../backend/src/main/resources/db/migration/V2__seed_data.sql
```

Le fichier `V2__seed_data.sql` fourni a déjà été généré à partir de ton fichier et validé (270 matchs, 58 équipes, 6 compétitions). Points à savoir :

- **Noms d'équipes = codes courts** ("G2", "LGD"...) : Feuil1 utilise des abréviations, pas les noms complets. Tu peux les renommer plus tard via l'écran "Équipes" de l'appli, ou étoffer le script si tu veux une table de correspondance code → nom complet.
- **19 lignes ignorées** : ce sont des créneaux de playoffs pas encore déterminés dans ton fichier (l'équipe qualifiée dépend d'un résultat pas encore joué). Normal, tu les ajouteras via l'appli une fois les équipes connues.
- Si tu relances le script après avoir modifié l'Excel, régénère `V2__seed_data.sql` **avant** le tout premier démarrage (Flyway ne rejoue pas une migration déjà appliquée sur une base existante — si la base a déjà tourné, repars d'une base vide ou ajoute une `V3__...sql` avec seulement les nouveautés).

## 8. Pistes d'évolution (hors v1)

- Vrai arbre de bracket pour les playoffs (rounds liés entre eux, affichage graphique) plutôt qu'une liste de matchs avec `roundLabel` libre.
- Import des feuilles MSI / Esports World Cup (format différent : groupes + bracket).
- Authentification, si l'appli doit un jour être accessible à d'autres que toi.
- Table de correspondance code ↔ nom complet d'équipe pour un import plus soigné.
- Dockerisation (Postgres, puis backend/frontend) une fois le projet stabilisé.

## Structure du repo

```
lol-results/
├── backend/    Spring Boot (Java 21, Maven, PostgreSQL, Flyway)
├── frontend/   Vue 3 + Vite
├── import/     Script Python de conversion Excel → SQL
└── README.md
```

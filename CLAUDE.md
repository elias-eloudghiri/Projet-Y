# Projet Yohan — Lecture coopérative

Site de lecture coopérative : 2 à 4 joueurs rejoignent une room protégée par mot de passe et lisent ensemble une histoire, chacun incarnant un ou plusieurs personnages. Spécifications complètes : `docs/cahier-des-charges.md` (à lire avant toute fonctionnalité nouvelle).

## Structure du repo

```
/front    → Vue.js
/back     → Java
/docs     → cahier des charges, notes
docker-compose.yml (à créer)
```

<!-- Adapter les noms de dossiers si différents -->

## Stack

| Brique | Choix |
| --- | --- |
| Front | Vue.js (dark mode uniquement) |
| Back | Java 25, Spring Boot 4.1, Maven, Flyway (migrations) |
| Temps réel | **À trancher** : Spring WebSocket (STOMP) dans le back Java, ou serveur Node + Socket.IO dédié |
| Base de données | PostgreSQL |
| Stockage fichiers | Stockage objet compatible S3, via AWS SDK v2 : SeaweedFS en dev/tests, tout fournisseur S3 en prod (MinIO écarté : plus maintenu). PDF des histoires (images incluses), sons |
| Auth | JWT : jeton d'accès court + refresh token, en cookies httpOnly |
| Conteneurs | Docker + docker-compose |

## Commandes

<!-- À compléter une fois le projet initialisé -->
- Prérequis : copier `.env.example` en `.env` et le remplir (lu par docker compose et par le back)
- Lancer l'environnement : `docker compose up -d` (PostgreSQL + SeaweedFS)
- Front (dev) : `cd front && npm run dev` (proxy `/api` → `localhost:8080`)
- Back (dev) : `cd back && ./mvnw spring-boot:run` (JDK 25 requis)
- Tests back : `cd back && ./mvnw test` (Testcontainers : Docker doit tourner)
- Vérifs front : `cd front && npm run type-check && npm run lint`

## Règles métier tranchées

- Rooms de 2, 3 ou 4 joueurs ; **le nombre est imposé par l'histoire**, pas choisi par le créateur.
- La room refuse tout joueur au-delà du nombre prévu.
- Seul le **créateur de room** doit avoir un compte ; les invités rejoignent avec un pseudo.
- Un invité ne peut pas créer de room.
- Pseudos générés aléatoirement (format type `lerat-000999`) pour éviter les pseudos gênants.
- Chaque joueur a une couleur unique ; couleur + pseudo remplacent la photo de profil.
- Un joueur peut incarner plusieurs personnages ; un personnage a exactement un joueur. Le narrateur est un personnage comme les autres.
- **Seul le créateur** modifie l'attribution (drag and drop), avant et pendant la lecture, sans relancer la partie.
- **Seul le créateur** fait avancer la lecture et lance la partie.
- Le créateur peut afficher une image en plein écran chez tous les joueurs.
- Seul le nom du personnage est coloré ; la réplique garde la couleur normale.
- Indicateur en haut d'écran, identique pour tous : couleur + pseudo du lecteur en cours.
- Audio (lot 2) : sons joués **chez tous en même temps**.
- Voix : outil externe (Discord…), pas de chat vocal intégré.
- Déconnexion d'un joueur : délai de retour ; passé ce délai, ses rôles sont réattribués s'il reste assez de joueurs, sinon la room est détruite.
- Seul l'**administrateur** ajoute des histoires au catalogue.
- Format des histoires : **PDF avec convention stricte**, analysé par l'application (point le plus risqué techniquement : parser robuste + messages d'erreur clairs à l'import).
- Login par pseudo généré (lerat-000999) + mot de passe ; le pseudo est à la fois identifiant et nom affiché ; pas d’email, pas de récupération
## Contraintes non fonctionnelles

- Synchronisation : changement visible chez tous en moins d'une seconde.
- Charge cible : 1000 rooms simultanées.
- Sécurité : mots de passe hachés (bcrypt/argon2), mots de passe de room jamais stockés en clair, HTTPS partout.
- RGPD : suppression de compte, aucun cookie publicitaire avant consentement (lot 3).

## Lots

1. **MVP** : comptes/JWT, invités, rooms, catalogue, attribution drag and drop, lecture synchronisée, indicateur de lecteur, images plein écran, dark mode.
2. **Audio** : musiques/effets déclenchés par commandes dans le texte, son de fin de réplique, soundboard.
3. **Monétisation** : encart pub à gauche, bandeau cookies.

Travailler lot par lot ; ne pas implémenter le lot 2 ou 3 sans demande explicite.

## Conventions

<!-- À compléter : langue du code/commits, style, conventions de nommage, stratégie de branches -->
- Code et noms techniques en anglais ; échanges et docs en français.
- Ne jamais committer de secrets : variables dans `.env` (non versionné), modèle dans `.env.example`.

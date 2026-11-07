# Cahier des charges — Projet Yohan (lecture coopérative)

Version du 30/09/2026, mise à jour avec les décisions client · Prestataire : Elias

## 1. Contexte et objectifs

Site de lecture coopérative : 2 à 4 joueurs se retrouvent dans une room protégée par mot de passe et lisent ensemble une histoire, chacun incarnant un ou plusieurs personnages.

- **Client** : [nom, structure, coordonnées]
- **Prestataire** : Elias, développeur freelance
- **Objectif** : rendre la lecture à plusieurs fluide et vivante (une couleur par joueur, indicateur de tour, ambiance sonore).
- **Cible** : [à compléter : clubs de lecture, théâtre amateur, rôlistes… ?]
- **Modèle économique** : revenus publicitaires via un encart à gauche de l'écran. [à confirmer]

## 2. Périmètre et lots

Trois lots, chacun chiffré, livré et recetté séparément.

| Lot | Contenu |
| --- | --- |
| Lot 1 · MVP | Inscription et connexion (JWT), accès invité, création et accès aux rooms par mot de passe, catalogue d'histoires (ajout par l'admin), tableau d'attribution par drag and drop, lecture synchronisée avec noms colorés, indicateur du joueur qui doit lire, images avec plein écran (local et partagé), dark mode |
| Lot 2 · Audio | Musiques et effets sonores déclenchés par des commandes dans le texte, son de fin de réplique, soundboard |
| Lot 3 · Monétisation | Encart publicitaire, bandeau de consentement cookies |

**Hors périmètre**, sauf avenant :

- Démarches de propriété intellectuelle (e-Soleau, brevet), à la charge du client.
- Chat vocal intégré (les joueurs utilisent un outil externe type Discord).

## 3. Acteurs et rôles

Seul le créateur de room doit obligatoirement avoir un compte.

| Rôle | Compte | Créer une room | Rejoindre une room | Modifier l'attribution | Faire avancer la lecture | Gérer le catalogue |
| --- | --- | --- | --- | --- | --- | --- |
| Créateur de room | Obligatoire | Oui | — | Oui | Oui | Non |
| Joueur inscrit | Oui | Oui | Oui | Non | Non | Non |
| Invité | Non, pseudo seul | Non | Oui | Non | Non | Non |
| Administrateur | Oui | — | — | — | — | Oui |

Règles communes :

- Une room accueille 2, 3 ou 4 joueurs ; **le maximum est imposé par l'histoire**, le créateur choisit le nombre de joueurs de la room entre 2 et ce maximum.
- Chaque joueur reçoit une couleur unique ; couleur et pseudo remplacent la photo de profil.
- Un joueur peut incarner plusieurs personnages ; un personnage a toujours exactement un joueur.
- Le narrateur est un personnage comme les autres.
- Les personnages sont réattribuables en cours de lecture, par le créateur uniquement.

## 4. Parcours utilisateur

1. Le créateur se connecte et crée une room à partir d'une histoire du catalogue.
2. Il transmet le mot de passe aux autres joueurs.
3. Les participants rejoignent la room avec le mot de passe.
4. Les participants s'identifient (compte ou pseudo invité).
5. Tous voient le tableau d'attribution ; le créateur l'ajuste.
6. Le créateur lance la lecture.
7. Le créateur fait avancer la lecture ; l'indicateur montre qui doit lire.
8. Fin de l'histoire.

Boucle : le créateur peut revenir à l'attribution pendant la lecture (étape 5 ↔ 7).

## 5. Spécifications fonctionnelles

Chaque fonctionnalité est une user story ; ses critères d'acceptation servent de base à la recette.

### 5.1 Comptes et accès (lot 1)

**US-01** · En tant que visiteur, je veux créer un compte et me connecter, afin de pouvoir créer des rooms.
- Inscription par [email + mot de passe ? à confirmer], connexion avec jeton JWT.
- Le pseudo est généré aléatoirement (ex. `lerat-000999`) pour éviter les pseudos gênants.
- Expiration de session et déconnexion gérées. [durée à définir]

**US-02** · En tant que visiteur sans compte, je veux rejoindre une room en invité avec un simple pseudo.
- Un invité ne peut pas créer de room.
- En cas de déconnexion, le joueur dispose d'un délai de retour [durée à définir]. Passé ce délai, ses personnages sont réattribués s'il reste assez de joueurs ; sinon la room est détruite.

**US-03** · En tant qu'administrateur, je veux ajouter une histoire au catalogue.
- L'administrateur importe le PDF de l'histoire et ses ressources (images, sons), et saisit le **nombre maximum de joueurs** (2 à 4).
- L'import échoue avec un message explicite si le PDF ne respecte pas la convention (voir section 8).

### 5.2 Rooms (lot 1)

**US-04** · En tant que créateur, je veux créer une room à partir d'une histoire du catalogue, afin d'y inviter mes joueurs.
- Le créateur choisit l'histoire, un mot de passe et le nombre de joueurs de la room, entre 2 et le maximum de l'histoire.
- La room refuse tout joueur au-delà du nombre prévu.

**US-05** · En tant que participant, je veux rejoindre une room avec son mot de passe.
- Un mot de passe erroné est refusé avec un message clair.
- À l'entrée, le joueur reçoit une couleur libre.

### 5.3 Tableau d'attribution (lot 1)

**US-06** · En tant que joueur, je veux voir avant la lecture un tableau des personnages de chaque joueur ; en tant que créateur, je veux le modifier par drag and drop.
- Chaque joueur apparaît avec sa couleur, son pseudo et ses personnages, narrateur compris.
- Seul le créateur peut déplacer des personnages.
- Un déplacement est visible chez tous les joueurs en moins d'une seconde. [seuil à valider]

**US-07** · En tant que créateur, je veux modifier l'attribution en cours de lecture, sans relancer la partie.
- La couleur du nom du personnage change immédiatement dans le texte, chez tous les joueurs.

### 5.4 Lecture (lot 1)

**US-08** · En tant que joueur, je veux voir le nom de chaque personnage dans la couleur de son joueur.
- Seul le nom est coloré ; la réplique garde la couleur de texte normale.

**US-09** · En tant que joueur, je veux savoir qui doit lire.
- Un indicateur en haut de l'écran, identique pour tous, affiche la couleur et le pseudo du lecteur en cours.
- Le créateur fait avancer la lecture ; la position est synchronisée chez tous. [interaction à préciser : bouton, clavier…]

**US-10** · En tant que joueur, je veux voir les images de l'histoire et pouvoir les passer en plein écran.
- Les images s'affichent telles quelles dans le fil du texte.

**US-10b** · En tant que créateur, je veux cliquer sur une image pour l'afficher en plein écran chez tous les joueurs.

### 5.5 Audio (lot 2)

**US-11** · En tant que joueur, je veux que musiques et effets sonores se lancent quand la lecture atteint une commande dans le texte.
- Les sons sont joués chez tous les joueurs en même temps.
- Syntaxe des commandes : [à définir, compatible avec la convention PDF]
- La piste musicale est prioritaire sur les effets sonores. [à préciser : ce que « prioritaire » implique]

**US-12** · En tant que joueur, je veux activer un son de fin de réplique depuis le panneau de commande audio.
- Le bouton est grisé si l'histoire ne contient pas de son de fin de réplique.

**US-13** · En tant que joueur, je veux une soundboard activable sur l'écran principal.
- Les sons sont entendus par tous les joueurs.

### 5.6 Publicité (lot 3)

**US-14** · En tant que client, je veux afficher de la publicité dans un encart à gauche de l'écran.
- Régie : [à définir]
- Aucun cookie publicitaire n'est déposé avant le consentement de l'utilisateur.
- Affichage sur mobile : [à définir]

## 6. Contraintes non fonctionnelles

| Exigence | Valeur |
| --- | --- |
| Thème | Dark mode uniquement |
| Navigateurs | [à définir : Chrome, Firefox, Safari, Edge, versions récentes ?] |
| Synchronisation | Changements visibles chez tous en moins d'une seconde [à valider] |
| Charge | 1000 rooms simultanées au lancement |
| Sécurité | Mots de passe hachés, HTTPS partout, mots de passe de room non stockés en clair |
| RGPD | Mentions légales, politique de confidentialité, consentement cookies, suppression de compte sur demande |
| Accessibilité | [à définir : niveau visé, contrastes suffisants en dark mode au minimum] |

## 7. Choix techniques

| Brique | Choix | Remarque |
| --- | --- | --- |
| Front-end | Vue.js | |
| Back-end | Java | |
| Temps réel | Spring WebSocket (STOMP) dans le back Java (tranché) | Les plateformes serverless gèrent mal les connexions persistantes ; même authentification et mêmes données que l'API, un service de moins |
| Base de données | PostgreSQL | Comptes, rooms, histoires, attributions |
| Authentification | JWT : jeton d'accès court + jeton de rafraîchissement | Jetons en cookie httpOnly |
| Fichiers | Stockage objet compatible S3 (SeaweedFS en dev, fournisseur S3 au choix en prod) | Histoires (PDF, images incluses), sons |
| Conteneurisation | Docker / docker-compose | |
| Hébergement | [à choisir : VPS ou autre] | Hébergement et nom de domaine à la charge de [client ?] |

## 8. Contenus

Le format des histoires est le principal risque technique : l'application doit savoir qui parle et où commence chaque réplique pour colorer les noms, indiquer le lecteur et déclencher les sons.

- **Choix retenu** : PDF avec convention stricte, analysé par l'application. Une faute de mise en page peut casser la lecture : la convention doit être documentée et l'import doit signaler précisément les erreurs.
- **Convention PDF** : proposition dans [convention-pdf.md](convention-pdf.md), à valider avec le client (styles Personnage / Réplique / Indication, noms réservés, images, erreurs d'import)
- **Syntaxe des commandes audio** : [à définir]
- **Fourniture** : histoires, images, musiques et sons fournis par [le client ?], dans les formats [MP3, OGG ?]
- **Droits** : le client garantit disposer des droits sur les textes, images et sons publiés.

## 9. Planning et livrables

Durées et dates à estimer. Chaque lot livré comprend le code source, son déploiement et une courte documentation technique ; le lot 3 se conclut par la mise en ligne. [à confirmer]

## 10. Recette et validation

Chaque lot est validé par le client sur la base des critères d'acceptation de la section 5.

- Le prestataire livre le lot sur un environnement de test accessible au client.
- Le client dispose de [X jours ouvrés] pour tester et signaler les anomalies par écrit.
- Le prestataire corrige les anomalies bloquantes, puis le client prononce la validation du lot.
- Sans retour dans le délai prévu, le lot est considéré comme validé.
- **Anomalie** : écart par rapport à un critère d'acceptation. Toute demande nouvelle relève d'un avenant.
- **Garantie** : correction gratuite des bugs signalés dans les [X mois] suivant la mise en ligne.

## 11. Aspects juridiques et commerciaux

[modalités à valider, idéalement avec un avis juridique]

- **Propriété du code** : cession des droits d'exploitation sur le code livré, à réception du paiement complet. Préciser les droits cédés, leur durée et leur territoire.
- **Code tiers** : les bibliothèques open source restent sous leur propre licence.
- **Paiement** : [ex. acompte de 30 % à la signature, puis un paiement à la validation de chaque lot]
- **Maintenance** : non incluse ; peut faire l'objet d'un contrat séparé.
- **Modifications** : toute demande hors de ce cahier des charges fait l'objet d'un devis complémentaire.
- **Propriété intellectuelle du concept** : l'enveloppe e-Soleau est une preuve datée de création, pas un brevet. Ces démarches restent à la charge du client.

## 12. Décisions et questions ouvertes

| Sujet | Décision |
| --- | --- |
| Format des histoires | PDF avec convention stricte |
| Progression de la lecture | Le créateur de la room |
| Voix | Outil externe (Discord…) |
| Audio | Joué chez tous en même temps |
| Attribution | Créateur seul |
| Déconnexion | Délai de retour, puis réattribution ou destruction de la room |
| Nombre de joueurs | Maximum imposé par l'histoire ; le créateur choisit entre 2 et ce maximum |
| Catalogue | Alimenté par l'admin ; droits sur les textes : [à confirmer] |
| Publicité | Régie et affichage mobile : [ouvert] |
| Temps réel | Spring WebSocket (STOMP) : tranché |

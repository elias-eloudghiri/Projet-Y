# Convention PDF des histoires

**Statut : proposition, à valider avec le client.**
Établie à partir de l'histoire « RED MOON — The Ending » (export Word, 69 pages), analysée ligne par ligne (police, taille, position). Elle formalise la mise en page que l'auteur utilise déjà et tranche les cas ambigus.

L'application lit le PDF à l'import : elle doit savoir qui parle, où commence chaque réplique et où se trouvent les images. Un PDF qui ne respecte pas la convention est **refusé à l'import**, avec la liste précise des erreurs (page, texte en cause, règle non respectée). Rien n'est deviné : mieux vaut un refus clair qu'une lecture faussée devant les joueurs.

## 1. Les trois styles

L'histoire s'écrit dans Word avec **trois styles**, et seulement trois. Ils sont prêts à l'emploi dans le modèle Word [modele-histoire.dotx](modele-histoire.dotx) : un double-clic crée une nouvelle histoire avec une page de couverture (mode d'emploi) et une scène d'exemple. Après un nom de personnage, Entrée passe tout seul au style Réplique.

| Style | Mise en forme | Alignement | Rôle |
| --- | --- | --- | --- |
| **Personnage** | Times New Roman, gras, 21 pt | Centré | Nom de celui qui parle, seul sur sa ligne |
| **Réplique** | Arial, 18 pt | Centré | Texte dit par le personnage |
| **Indication** | Aptos, 15 pt | Aligné à gauche | Narration et transitions de scène (« Plus tard », « Ailleurs »…) |

C'est la police et la taille qui permettent à l'application de reconnaître chaque élément : un nom de personnage tapé en Arial sera lu comme une réplique. Le gras, l'italique ou une taille plus grande **à l'intérieur** d'une réplique sont permis (emphase) et n'ont pas d'effet sur l'analyse.

## 2. Répliques

```
                Yann                      ← Personnage
   C'est bon, ça devrait suffire          ← Réplique
                Alice                     ← Personnage
  Wow, elle fonctionne ! J'ai une         ← Réplique (une ou plusieurs lignes,
     vraie prothèse métallique !            un ou plusieurs paragraphes)
```

- Une réplique commence au nom du personnage et s'arrête au nom suivant ou à la prochaine indication.
- Elle peut contenir plusieurs paragraphes (par exemple `*soupire* *respire*`, puis `Je suis calme`) : ils restent attribués au même personnage.
- Une réplique peut être coupée par un saut de page : aucun problème, la mise en page des pages n'est pas prise en compte.
- **Didascalies** entre astérisques (`*se lève*`) et **pensées** entre parenthèses (`(Il faut que j'arrête de l'approcher)`) font partie de la réplique et sont lues par le même joueur.
- Un nom sans réplique derrière lui est une erreur.

## 3. Noms de personnages

- Le nom doit correspondre **exactement** à l'un des personnages déclarés par l'administrateur lors de l'import. Les majuscules et les espaces en début ou fin de nom ne comptent pas : `MONODOM` et `Monodom` désignent le même personnage, mais `Monodom` et `Monodome` non.
- Pas de ponctuation après le nom : `Alice` et non `Alice :`.
- Pas de précision dans le nom : `Draculord` et non `Draculord (et une horde de robots)`. La précision va dans la réplique ou dans une indication.
- Un nom inconnu bloque l'import : l'erreur indique la page et le nom trouvé, et propose le personnage déclaré le plus proche.

### Noms réservés

| Écriture | Sens | Qui lit | Affichage |
| --- | --- | --- | --- |
| `Tout le monde` | Réplique dite en chœur | Tous les joueurs | Nom en couleur de texte normale |
| `Tout le monde sauf Mike` | Chœur avec exceptions | Tous les joueurs sauf ceux de Mike | Couleur de texte normale |
| `Yann et Rouge` | Plusieurs personnages ensemble (2 ou plus, séparés par des virgules et un « et » final) | Les joueurs de chacun | Chaque nom dans la couleur de son joueur |
| `??? (Spade)` | Personnage dont l'identité est encore cachée | Le joueur de Spade | `???` seul, en couleur de texte normale, pour ne pas révéler qui parle |

Les noms utilisés dans ces formes (Mike, Yann, Rouge, Spade…) doivent eux aussi être des personnages déclarés. Un `???` seul, sans personnage entre parenthèses, est refusé : l'application ne saurait pas à qui donner la réplique.

## 4. Narration et transitions

- Le **Narrateur** est un personnage comme les autres : il est attribué à un joueur et son nom est coloré.
- Il y a deux façons équivalentes d'écrire de la narration :
  1. explicitement, avec le nom `Narrateur` en style Personnage suivi d'une réplique ;
  2. en style **Indication** : tout texte dans ce style est attribué au Narrateur.
- Les transitions de scène (« Plus tard », « Ailleurs », « Du côté de Yann », « Flashback », « Retour au présent », « Générateurs activés : 1/5 »…) s'écrivent en style Indication : elles sont lues par le Narrateur.
- Une réplique ne doit jamais être écrite en style Indication : elle serait donnée au Narrateur.

## 5. Images

- Les images sont insérées dans le texte, entre deux blocs, à l'endroit où elles doivent apparaître pendant la lecture. Aucun marqueur n'est nécessaire.
- Elles sont extraites à l'import ; le créateur de la room peut ensuite les afficher en plein écran chez tous les joueurs.
- Formats : ceux que Word insère (JPEG, PNG). Une image doit faire au moins 300 pixels de large pour rester lisible en plein écran. [à confirmer]

## 6. Ignoré par l'application

- La **page de titre** (première page) : le titre et la description viennent du formulaire d'import.
- Les **numéros de page**, en-têtes et pieds de page.
- La **bordure de page** si c'est un trait simple (dessin vectoriel). Une bordure artistique (motif répété) est exportée en images et serait prise pour des illustrations : elle est interdite.
- La mention finale (« A SUIVRE ») si elle est dans un style autre que les trois styles ci-dessus. [à confirmer]

## 7. Réservé pour le lot 2 (audio)

Les crochets sont réservés aux commandes : `[son : pluie]`, `[musique : combat]`, `[stop musique]`. La syntaxe exacte sera définie avec le lot 2 ; d'ici là, un texte entre crochets dans une histoire est signalé à l'import pour éviter un conflit futur.

## 8. Erreurs signalées à l'import

Toutes les erreurs sont listées en une fois, pas seulement la première.

| Erreur | Exemple de message (illustratif) |
| --- | --- |
| Nom inconnu | Page 38 : « Monodome » n'est pas un personnage déclaré. Vouliez-vous dire « MONODOM » ? |
| Ponctuation dans un nom | Page 3 : « Alice : » — retirez le « : » après le nom. |
| Nom sans réplique | Page 12 : « Wendy » n'est suivi d'aucune réplique. |
| Mystère sans personnage | Page 38 : « ??? » doit préciser le personnage, par exemple « ??? (Spade) ». |
| Texte hors des trois styles | Page 12 : « Bonjour » n’est ni un nom, ni une réplique, ni une indication (police Calibri 14). |
| Personnage déclaré jamais utilisé | « Ruby » est déclaré mais ne parle jamais dans l'histoire. (avertissement, n'empêche pas l'import) |
| Crochets | Page 20 : « [rire] » — les crochets sont réservés aux commandes audio. |

## 9. Corrections à faire dans RED MOON

Repérées lors de l'analyse ; avec ces corrections, l'histoire respecte la convention.

| Page | Problème | Correction |
| --- | --- | --- |
| 2, 32 | `???` sans personnage (Spade) | `??? (Spade)` |
| 38 | `???` (MONODOM), écrit en style Indication | `??? (MONODOM)` en style Personnage |
| 3 | `Alice :` | `Alice` |
| 29 | `Draculord (et une horde de robots)` | `Draculord`, la précision en indication |
| 16, 17, 21, 39, 68 | Transitions et narration en Arial 18 (style Réplique) : « Le lendemain », « Stella arriva au campement de fortune », « Plus tard », « Ailleurs », « Spirit forrest », « Générateurs activés : 4/5 »… | Style Indication |
| 19, 54 | « Plus tard », « Flashback », « Retour au présent » centrés en Arial 18 | Style Indication |
| 30, 31, 37, 38, 40–43, 52, 57 | Répliques ou didascalies écrites en style Indication : `*pars*`, `*silence*`, `*insère la clé*`, `*attend*`, `*clank*`, `...`, `AU SECOURS !`… | Style Réplique sous le bon personnage, ou laisser en Indication si c'est le Narrateur qui les lit |
| 69 | `Je compte sur vous` en style Indication après `Mike et Rouge` | Style Réplique si ce sont Mike et Rouge qui le disent |
| — | Personnages à déclarer à l'import | Narrateur, Spade, Alice, Yann, Mike, Mohliot, Wendy, Stella, Rouge, Luminès, MONODOM, Draculord, Ruby |

## 10. Questions ouvertes pour le client

1. Les transitions (« Plus tard », « Ailleurs ») doivent-elles être **lues à voix haute** par le Narrateur, ou seulement affichées ?
2. Les noms réservés (`Tout le monde`, `X et Y`, `??? (Nom)`) conviennent-ils ? D'autres cas existent-ils dans les autres histoires ?
3. Le modèle Word fourni ([modele-histoire.dotx](modele-histoire.dotx)) convient-il à l’auteur pour ses prochaines histoires ?
4. Les polices (Times New Roman, Arial, Aptos) peuvent-elles être imposées pour toutes les histoires ? Si l'auteur veut d'autres polices, il faudra les fixer ici une fois pour toutes.
5. Une image doit-elle pouvoir porter une légende ?

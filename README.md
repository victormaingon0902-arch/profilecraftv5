# ProfileCraft — Paper 26.3 (Java 25, Maven)

* **Shift + clic droit** sur un joueur (ou `/profile [joueur]`) → menu de profil : rang LuckPerms (préfixe),
  temps de jeu, joueurs tués, morts par un joueur, ratio PvP, morts totales, monstres tués, têtes collectées,
  Discord lié, dates de connexion.
* **Têtes de joueurs** : la victime d'un PvP laisse sa tête **avec son vrai skin** (texture mémorisée, jamais Steve).
  `/head` ouvre le menu des têtes de tous les joueurs que vous avez tués.
* **`/link discord`** : liaison Minecraft ↔ Discord → rôle **Membre** automatique, compte Discord affiché dans le profil.
* **Pont de chat** Minecraft ↔ Discord (+ connexions, déconnexions, morts, démarrage/arrêt du serveur).

Les statistiques viennent des **statistiques vanilla** du serveur : l'historique existant est donc pris en compte
(les têtes de `/head`, elles, ne se remplissent qu'à partir de l'installation du plugin).

## Compiler
JDK 25 + Maven :

```
mvn clean package
```
→ `target/ProfileCraft-1.0.0.jar` (≈ 12 Mo : le bot Discord JDA est embarqué) dans `plugins/`.
LuckPerms est **optionnel** (sans lui, la ligne « Rang » affiche « Aucun »).

## Commandes
| Commande | Rôle |
|---|---|
| `/profile [joueur]` (`/profil`) | ouvre un profil (aussi hors-ligne) |
| `/head` (`/tetes`) | têtes des joueurs que vous avez tués |
| `/link discord` · `/link unlink` · `/link status` | liaison Discord |
| `/profilecraft reload\|status\|unlink <joueur>` | administration (`profilecraft.admin`) |
| **Discord** : `/link code:XXXXXX` · `/unlink` | commandes slash du bot |

## Configurer Discord, pas à pas

### 1. Créer le bot
1. Allez sur <https://discord.com/developers/applications> → **New Application** → donnez un nom.
2. Menu **Bot** → **Reset Token** → copiez le token (affiché une seule fois ; ne le partagez jamais).
3. Toujours dans **Bot**, section *Privileged Gateway Intents*, activez :
   * **Server Members Intent**
   * **Message Content Intent**
   (sans eux : erreur `DISALLOWED_INTENTS` au démarrage).

### 2. Inviter le bot sur votre serveur
Remplacez `VOTRE_CLIENT_ID` (page **General Information → Application ID**) puis ouvrez le lien :

```
https://discord.com/oauth2/authorize?client_id=VOTRE_CLIENT_ID&permissions=402738176&scope=bot%20applications.commands
```
Il donne au bot : voir les salons, envoyer des messages, intégrer des liens, lire l'historique, **gérer les rôles**
et gérer les pseudos (utile seulement si `sync-nickname: true`).

### 3. Récupérer les identifiants
Discord → **Paramètres utilisateur → Avancés → Mode développeur** (activé), puis clic droit → **Copier l'identifiant** sur :
* l'**icône du serveur** → `guild-id`
* le **rôle « Membre »** (Paramètres du serveur → Rôles ; créez-le s'il n'existe pas) → `member-role-id`
* le **salon de chat** à relier → `chat.channel-id`

### 4. Hiérarchie des rôles (étape oubliée par presque tout le monde)
Paramètres du serveur → **Rôles** : faites glisser le rôle du bot (il porte le nom du bot) **au-dessus** du rôle « Membre ».
Un bot ne peut attribuer que des rôles situés sous le sien.

### 5. Remplir `plugins/ProfileCraft/config.yml`
```yaml
discord:
  enabled: true
  token: ""                    # laissez vide et définissez la variable d'environnement DISCORD_TOKEN (recommandé)
  guild-id: "123456789012345678"
  member-role-id: "123456789012345678"
  invite-url: "https://discord.gg/votre-invitation"
  chat:
    channel-id: "123456789012345678"
```
Redémarrez le serveur (ou `/profilecraft reload`). Console : `Bot Discord connecté` puis `Commandes slash ... enregistrées`.

### 6. Tester
1. En jeu : `/link discord` → un code à 6 caractères (valable 10 min) s'affiche.
2. Sur Discord, dans n'importe quel salon : tapez `/link`, choisissez la commande du bot et collez le code.
3. Le rôle **Membre** est attribué, le joueur voit « compte lié », et son profil affiche son Discord.
   `/unlink` (Discord) ou `/link unlink` (jeu) supprime la liaison et retire le rôle ; si le membre quitte le Discord,
   la liaison est supprimée ; s'il revient, le rôle lui est rendu tant que la liaison existe.

### Bonus
* `discord.link-commands: ["eco give %player% 1000"]` → récompense à la liaison (commande de BountyCraft).
* `discord.sync-nickname: true` → le pseudo Discord devient le pseudo Minecraft.
* `discord.activity: "play.monserveur.fr"` → statut du bot.

### Dépannage
| Symptôme | Cause probable |
|---|---|
| `DISALLOWED_INTENTS` | les 2 intents de l'étape 1.3 ne sont pas activés |
| `/link` n'apparaît pas sur Discord | bot invité sans `applications.commands`, mauvais `guild-id`, ou recharger Discord (Ctrl+R) |
| « Impossible de donner le rôle » | rôle du bot sous « Membre » (étape 4) ou permission *Gérer les rôles* absente |
| Rien ne passe dans le chat | mauvais `channel-id`, ou le bot n'a pas accès à ce salon |
| Aucune connexion | le serveur doit pouvoir joindre `discord.com` et `gateway.discord.gg` (port 443) |

## Notes techniques
* Les liens sont dans `plugins/ProfileCraft/data.yml` (avec les textures des skins, pour fabriquer les têtes hors-ligne).
* Les mentions (`@everyone`, `@rôle`) sont **désactivées** dans les messages envoyés vers Discord.
* Le message Discord → Minecraft n'interprète pas MiniMessage (aucune injection de formatage/commande cliquable).


## Annonces administrateur Discord

Renseignez `discord.admin-channel-id` avec l'identifiant du salon privé d'administration et, si nécessaire, `discord.admin-role-id` avec l'identifiant du rôle autorisé. Les membres ayant la permission Discord Administrateur sont également autorisés. Dans les paramètres Discord, rendez ce salon visible uniquement par les administrateurs et le rôle choisi. Utilisez `/annonce message:<texte>` dans ce salon : le bot publie une intégration rouge et affiche le message en rouge sur l'écran des joueurs Minecraft.

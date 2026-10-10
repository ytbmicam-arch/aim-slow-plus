# Aim Slow Plus (français)

Mod **côté client** Fabric pour **Minecraft Java 1.21.11** : la sensibilité de la souris diminue
progressivement quand ton viseur s'approche d'une entité, pour viser plus précisément.

> **Ce n'est pas une aide à la visée.** Le mod ne fait jamais tourner la caméra et n'attire jamais
> le viseur vers une cible. Il multiplie seulement le mouvement brut de la souris par un nombre
> compris entre ta « sensibilité minimale » et 1,0.

## Compiler le JAR

JDK 21 ou plus récent requis :

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

Résultat : `build/libs/aim-slow-plus-1.0.0.jar` (ignore le `-sources.jar`). Sans Java installé, pousse le
dossier sur GitHub : l'onglet **Actions** compile le mod et propose le JAR en téléchargement.

## Installer dans Prism Launcher

1. Crée une instance **1.21.11** avec le chargeur **Fabric**.
2. **Modifier** > **Mods** > **Télécharger des mods** > **Modrinth** : installe **Fabric API**
   (et, en option, **Mod Menu**).
3. Toujours dans **Mods**, clique sur **Ajouter un fichier** et choisis `aim-slow-plus-1.0.0.jar`.
4. Lance le jeu. Les touches sont dans **Options > Contrôles > Touches** (catégorie « Aim Slow Plus »).

La touche **K** active/désactive le mod. Les réglages s'ouvrent depuis Mod Menu (bouton de
configuration) ou avec la touche « Ouvrir les réglages » (non assignée par défaut).

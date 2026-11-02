# MTR France Addon

This branch contains the Forge 1.7.10 / GT New Horizons port. Use the [GTNH installation and build guide](gtnh/README.md). The upstream project guide follows below.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5--1.20.4-green.svg)](https://minecraft.net/)
[![MTR](https://img.shields.io/badge/MTR-4.0.0+-blue.svg)](https://minecrafttransitrailway.com/)
![Fabric](https://img.shields.io/badge/Fabric-Supported-blue.svg)
![Forge](https://img.shields.io/badge/Forge-Supported-orange.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)

[![crowdin](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/translate/crowdin_46h.png)](https://crowdin.com/project/mtr-france-addon)
[![Crowdin](https://badges.crowdin.net/mtr-france-addon/localized.svg)](https://crowdin.com/project/mtr-france-addon)

[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_64h.png)](https://github.com/MTR-France-Team/MTR-France-Addon)
[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/project/YJct9p8I)

![fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/fabric_46h.png)
![forge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/forge_46h.png)

Un addon pour le mod [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/tree/master) qui vise à ajouter des éléments, signalétiques et décorations du réseau ferroviaire français.

---

## 📋 Prérequis

- **Java Development Kit (JDK)** :
    - JDK 8 pour Minecraft 1.16.5
    - JDK 16 pour Minecraft 1.17.1
    - JDK 17 pour Minecraft 1.18.2 - 1.20.4
    - JDK 21 pour Minecraft 1.20.5+
- **Gradle** : Inclus via le wrapper (`gradlew`)
- **Git** : Pour cloner le repository

---

## 🔧 Compilation du Projet

Ce projet supporte **deux méthodes de compilation** pour générer les JARs Fabric et Forge :

### Méthode 1 : Compilation Manuelle (Version Spécifique)

Cette méthode permet de compiler le mod pour une version spécifique de Minecraft.

#### Étapes :

1. **Cloner le repository**
   ```bash
   git clone https://github.com/[votre-username]/MTRFranceAddon.git
   cd MTRFranceAddon
   ```

2. **Configurer la version cible**

   Modifiez le fichier `gradle.properties` à la racine pour définir la version Minecraft :
   ```properties
   minecraft_version=1.20.4
   ```

   Versions supportées : `1.20.4`, `1.20.1`, `1.19.4`, `1.19.2`, `1.18.2`, `1.17.1`, `1.16.5`

3. **Synchroniser les fichiers communs**

   Cette étape copie les fichiers Java et ressources partagés vers les modules Forge :
   ```bash
   ./gradlew setupFiles
   ```

4. **Compiler le projet**
   ```bash
   ./gradlew clean build
   ```

5. **Récupérer les JARs**

   Les fichiers compilés se trouvent dans :
    - `fabric/build/libs/MTRFRA-fabric-[version].jar`
    - `fabric/build/libs/MTRFRA-forge-[version].jar`

### Méthode 2 : Compilation Automatique (Toutes les Versions)

Cette méthode utilise le script `buildAll.sh` pour compiler automatiquement le mod pour toutes les versions supportées.

#### Étapes :

1. **Cloner le repository** (si pas déjà fait)
   ```bash
   git clone https://github.com/[votre-username]/MTRFranceAddon.git
   cd MTRFranceAddon
   ```

2. **Rendre le script exécutable** (Linux/macOS)
   ```bash
   chmod +x buildAll.sh
   ```

3. **Lancer la compilation complète**
   ```bash
   ./buildAll.sh
   ```

   Sur Windows avec Git Bash :
   ```bash
   sh buildAll.sh
   ```

   4. **Récupérer les JARs**

      Tous les fichiers compilés sont automatiquement copiés dans le dossier `releases/` :
      ```
      releases/
      ├── MTRFRA-fabric-1.20.4.jar
      ├── MTRFRA-forge-1.20.4.jar
      ├── MTRFRA-fabric-1.20.1.jar
      ├── MTRFRA-forge-1.20.1.jar
      └── ...
      ```

---

## 📁 Structure du Projet

```
MTRFranceAddon/
├── fabric/                 # Module Fabric
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/      # Code source Fabric + Common
│   │   │   └── resources/  # Ressources (textures, modèles, etc.)
│   └── build.gradle
├── forge/                  # Module Forge
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/      # Code spécifique Forge (copié depuis Fabric)
│   │   │   └── resources/  # Ressources (copiées depuis Fabric)
│   └── build.gradle
├── build/                  # Fichiers de build temporaires
├── releases/               # JARs finaux (Méthode 2)
├── gradle.properties       # Configuration principale
├── settings.gradle         # Configuration multi-modules
├── buildAll.sh            # Script de compilation automatique
└── README.md
```

---

## 🚀 Installation du Mod

1. **Installer Minecraft** avec la version souhaitée
2. **Installer le mod loader** approprié :
    - [Fabric Loader](https://fabricmc.net/) pour la version Fabric
    - [Minecraft Forge](https://files.minecraftforge.net/) pour la version Forge
3. **Installer les dépendances** :
    - [Minecraft Transit Railway (MTR)](https://modrinth.com/mod/minecraft-transit-railway) version 4.0.0+
    - [Fabric API](https://modrinth.com/mod/fabric-api) (Fabric uniquement)
4. **Placer le JAR** de MTR France Addon dans le dossier `mods/`

---

## ⚙️ Notes Techniques

### Tâches Gradle Principales

- `setupFiles` : Synchronise les fichiers communs entre Fabric et Forge
- `clean` : Nettoie les fichiers de build
- `build` : Compile le projet pour les deux loaders
- `shadowJar` : Package les dépendances nécessaires

### Versions Java Requises

Le projet détecte automatiquement la version Java requise selon Minecraft :

| Minecraft | Java |
|-----------|------|
| 1.16.5    | 8    |
| 1.17.1    | 16   |
| 1.18.2-1.20.4 | 17 |
| 1.20.5+   | 21   |

### Compatibilité Cross-Version

Le projet utilise Manifold Preprocessor pour gérer les différences entre versions de Minecraft, permettant un code source unique pour toutes les versions supportées.

---

## 🐛 Dépannage

### Erreur "Permission denied" sur Linux/macOS
```bash
chmod +x gradlew buildAll.sh
```

### Erreur de compilation Java
Vérifiez que vous utilisez la bonne version de Java pour votre version Minecraft cible.

### Les fichiers ne sont pas copiés vers Forge
Assurez-vous d'exécuter `./gradlew setupFiles` avant la compilation.

---

## 📝 Licence

Ce projet est sous licence MIT. Voir le fichier [LICENSE](LICENSE.txt) pour plus de détails.

---

## 🤝 Contribution

Les contributions sont les bienvenues ! N'hésitez pas à :
- Signaler des bugs via les Issues
- Proposer de nouvelles fonctionnalités
- Soumettre des Pull Requests

---

## 📞 Contact

Pour toute question ou suggestion, n'hésitez pas à ouvrir une Issue sur GitHub.

# Building RiffedCheats for NeoForge 1.21.1

1. **Java 21 JDK**
   - Fedora: `sudo dnf install java-21-openjdk-devel`
   - Other: https://adoptium.net/ (Temurin 21)
2. **Gradle wrapper** (this zip does not include one). Easiest way:
   - Download the MDK: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle (green "Code" button, Download ZIP)
   - Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from it into this project's root folder.
   - Alternative: install Gradle (https://gradle.org/install/) and run `gradle wrapper` here.
3. Build:
   ```
   chmod +x gradlew
   ./gradlew build
   ```
   The jar is `build/libs/riffedcheats-neoforge-1.21.1-1.0.0.jar`.
4. Test without installing: `./gradlew runClient`
5. If the compiler says `org.spongepowered.asm.mixin does not exist`, open `build.gradle` and follow the comment in the `dependencies` block.

The ModDevGradle plugin and NeoForge itself are downloaded automatically from https://maven.neoforged.net.

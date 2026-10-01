# Building RiffedCheats for Fabric 26.2

1. **Java 25 JDK** (Minecraft 26.x requires it). It must be the JDK, not only the runtime:
   - Fedora: `sudo dnf install java-25-openjdk-devel` (the `-devel` package contains `javac`)
   - Check with `javac -version` (must print 25.x). If it says "command not found", only the runtime is installed.
   - Error "does not provide the required capabilities: [JAVA_COMPILER]" = the -devel package is missing.
   - No Java 25 in your repo? Use Temurin 25 from https://adoptium.net/
2. **Gradle wrapper 9.5.1** (this zip does not include one). Easiest way:
   - Download the Fabric example mod: https://github.com/FabricMC/fabric-example-mod
   - Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from it into this project's root folder.
   - Or the template generator: https://fabricmc.net/develop/template/ (pick 26.2), then copy the same files.
3. Build:
   ```
   chmod +x gradlew
   ./gradlew build
   ```
   The jar is `build/libs/riffedcheats-fabric-26.2-1.0.0.jar`.
4. Test without installing: `./gradlew runClient`
5. Versions used (in `gradle.properties`, check https://fabricmc.net/develop for newer ones):
   Minecraft 26.2, Fabric Loader 0.19.5, Loom 1.17-SNAPSHOT, Fabric API 0.161.0+26.2

This port was written against the published 26.2 documentation without being able to compile it.
If the build prints errors, they will be in `Compat.java`, `Gfx.java`, `MenuScreen.java` or `mixin/BlockMixin.java`.
Those four files contain every Minecraft-version-specific call.

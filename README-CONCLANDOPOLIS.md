# ConcLandopolis

A MicropolisJ fork with ConcLand buildings and art.

## Build

Requires JDK 8 or newer (tested with JDK 17) and Apache Ant on your PATH (Ant is not bundled).

From the repo root:

```
ant
```

This produces `ConcLandopolis.jar` in the repo root.

## Run

```
java -jar ConcLandopolis.jar
```

On Windows you can also run `ConcLandopolis.bat`. It uses `%JAVA_HOME%\bin\java.exe` if `JAVA_HOME` is set, otherwise `java` from PATH.

City saves go to a `Save` folder in the directory you run the game from (the `.bat` runs from its own folder). The folder is created on first use.

## License / credit

Based on MicropolisJ by Jason Long, itself based on the Micropolis (SimCity) source released by Electronic Arts under the GPL.
Licensed under GPLv3 with additional terms. See `README` and `COPYING` for the full upstream credits and license.
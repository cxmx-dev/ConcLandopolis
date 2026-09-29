# ConcLandopolis

A MicropolisJ fork with ConcLand buildings and art.

## Build

Requires JDK 17 and Apache Ant. From the project folder:

```
ant
```

This produces `ConcLandopolis.jar`.

## Run

```
java -jar ConcLandopolis.jar
```

Or on Windows, double-click `ConcLandopolis.bat` (uses `%JAVA_HOME%` if set, otherwise `java` on PATH).

City saves go to `./Save` (created on first use, relative to the working directory).

## License / credit

Based on MicropolisJ by Jason Long, itself based on the Micropolis (SimCity) source released by Electronic Arts under the GPL.
Licensed under GPLv3 with additional terms. See `README` and `COPYING` for the full upstream credits and license.
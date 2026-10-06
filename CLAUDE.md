# CLAUDE.md – Projektkonventionen Zocks Zleep

Begleit-App für smarte Schlafsocken (Heizen, Massage, Schlaftracking per PPG).
Wellnessprodukt, kein Medizinprodukt. Geplant für den Play Store.

## Befehle

```bash
./gradlew assembleDebug        # muss nach jeder Änderung bauen
./gradlew testDebugUnitTest    # Unit- + Robolectric-UI-Tests, müssen grün sein
./gradlew lintDebug            # 0 Fehler, Warnungen beheben statt unterdrücken
```

Tests und Lint als getrennte Gradle-Aufrufe starten (gemeinsam kann Lint an KSP-Ausgaben scheitern).

## Architektur

- Single-Activity, Compose, Material 3, Navigation Compose mit `@Serializable`-Routen
  (`ui/navigation/Destinations.kt`).
- MVVM. Pakete: `ui` (Screens, ViewModels), `domain` (reines Kotlin), `data` (Room,
  DataStore, Repositories), `device` (SockDevice, Simulator, BLE), `di` (Hilt-Module).
- Abhängigkeiten zeigen nach innen: `ui → domain ← data/device`. `domain` importiert nie
  `android.*`, `androidx.*`, `ui`, `data` oder `device` (`DomainPurityTest`).
- ViewModels stellen genau einen `StateFlow<UiState>` bereit, Screens sind zustandslos
  (`XxxScreen(state, onEvent)`) plus eine dünne `XxxRoute`, die das ViewModel holt.
- Coroutines + Flow, keine Callbacks nach außen. Dispatcher werden injiziert.
- Alle Gerätefunktionen laufen über das Interface `SockDevice`; ein Paar (`left`/`right`)
  wird standardmäßig gemeinsam gesteuert (`SockSide.BOTH`).
- BLE-UUIDs und Datenformat stehen ausschließlich in `device/ble/SockBleProtocol.kt`.

## Messwerte und Sicherheit

- Nicht gelieferte Sensorwerte sind `null` und werden als „nicht verfügbar“ angezeigt.
  Niemals Werte schätzen oder auffüllen, außer im `SimulatedSockDevice`.
- Heizbefehle gehen immer durch den `HeatSafetyGuard` (Obergrenze, maximale Laufzeit,
  Plausibilitätsprüfung). Grenzwerte: Ziel 20–40 °C, harte Abschaltung > 42 °C,
  max. 90 min am Stück, unplausibel < 10 °C, > 50 °C oder > 3 °C Sprung in 10 s.
- Erkenntnisse als Zusammenhang formulieren, nie als Ursache.

## UI

- Dunkles Theme ist Standard. Farben nur über `MaterialTheme.colorScheme` bzw.
  `ZocksThemeExt.colors` (heat, sleep, massage, stage*), keine Hex-Werte in Screens.
- Wärme-Orange nur für Wärme. `secondary` ist bewusst neutral (Material nutzt es für viele
  Standardelemente).
- Bedienelemente für Heizen/Massage mindestens `Dimens.ThumbTarget` (64 dp).
- Jeder Screen hat Lade-, Leer- und Fehlerzustand (`ui/components/StateViews.kt`).
- Dekorative Icons: `contentDescription = null`. Bedeutungstragende Gruppen mit
  `semantics(mergeDescendants = true)`. Überschriften mit `semantics { heading() }`.
- Test-Tags in `snake_case` (`screen_home`, `nav_nights`, `action_heat`).

## Texte

- Alle Texte in `res/values/strings.xml` (Deutsch, Standard) und
  `res/values-en/strings.xml` (Englisch). Keine festen Strings im Code.
- Du-Form, kurz und ruhig. Platzhalter positionsgebunden (`%1$s`).

## Code-Stil

- Kotlin official style, max. 120 Zeichen, 4 Leerzeichen.
- Kommentare und KDoc auf Deutsch, Bezeichner auf Englisch.
- Versionen nur im Version Catalog `gradle/libs.versions.toml`.

## Tests

- Unit-Tests unter `app/src/test` (JUnit4, Truth, coroutines-test; Turbine ab Phase 2).
- UI-Tests ebenfalls unter `app/src/test` mit Robolectric (`@HiltAndroidTest`,
  `@Config(application = HiltTestApplication::class)`), Compose-Test-API v2.
  Robolectric läuft mit SDK 36 (`src/test/resources/robolectric.properties`).
- Algorithmen (Schlafphasen, Score, Routinen, Sicherheit) brauchen eigene Unit-Tests.

## Git

- Ein Commit pro Phase bzw. abgeschlossenem Schritt, Nachricht auf Deutsch.
- README-Abschnitt „Stand der Phasen“ bei jedem Phasenabschluss aktualisieren.

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

- Single-Activity, Compose, Material 3, Navigation Compose mit `@Serializable`-Zielen
  `XxxDestination` (`ui/navigation/Destinations.kt`). `XxxRoute` ist dagegen die Composable,
  die das ViewModel holt.
- MVVM. Pakete: `ui` (Screens, ViewModels), `domain` (reines Kotlin), `data` (Room,
  DataStore, Repositories), `device` (SockDevice, Simulator, BLE), `di` (Hilt-Module).
- Abhängigkeiten zeigen nach innen: `ui → domain ← data/device`. `domain` importiert nie
  `android.*`, `androidx.*`, `ui`, `data` oder `device` (`DomainPurityTest`).
- ViewModels stellen genau einen `StateFlow<UiState>` bereit, Screens sind zustandslos
  (`XxxScreen(state, onEvent)`) plus eine dünne `XxxRoute`, die das ViewModel holt.
- Coroutines + Flow, keine Callbacks nach außen. Dispatcher werden injiziert.
- Alle Gerätefunktionen laufen über das Interface `SockDevice` (`domain/device`); ein Paar
  (`SockPair`, `left`/`right`) wird standardmäßig gemeinsam gesteuert (`SockSide.BOTH`).
  Das aktive Paar (Simulator oder BLE) liefert `SockPairProvider`.
- Interfaces (Repositories, `SockDevice`, `SimulatorController`) liegen in `domain`,
  Umsetzungen in `data` bzw. `device`, verdrahtet in `di`.
- Einmalige Meldungen: `userMessage` (`@StringRes` oder `UiText` mit Platzhaltern) im
  UiState + `UserMessageEffect`. App-weite Heizmeldungen kommen über `AppViewModel`.
- Regler (`HeatController`, `MassageController`, `ControlCoordinator`) sind reines Kotlin
  und werden in `di/ControlModule` verdrahtet; sie laufen im App-Scope weiter, auch wenn
  kein Screen offen ist.
- Rechenintensives (z. B. Demo-Daten) auf dem injizierten `@DefaultDispatcher`.
- BLE-UUIDs und Datenformat stehen ausschließlich in `device/ble/SockBleProtocol.kt`.

## Daten

- Room-Datenbank `zocks.db`, Schema versioniert unter `app/schemas/`. Bei Schemaänderungen
  Version erhöhen und Migration schreiben (kein destruktiver Fallback).
- Messwerte werden pro Socke auf 30-s-Epochen verdichtet (`EPOCH_LENGTH`), Zeitpunkte als
  UTC-Millisekunden. Eine Nacht gehört zum Kalendertag ihres Beginns minus 12 h.
- Einstellungen in DataStore (`DataStoreSettingsRepository`), unbekannte Werte → Standard.

## Messwerte und Sicherheit

- Nicht gelieferte Sensorwerte sind `null` und werden als „nicht verfügbar“ angezeigt.
  Niemals Werte schätzen oder auffüllen, außer im `SimulatedSockDevice`.
- Heizbefehle gehen immer durch den `HeatSafetyGuard` (Obergrenze, maximale Laufzeit,
  Plausibilitätsprüfung). Grenzwerte: Ziel 20–40 °C, harte Abschaltung > 42 °C,
  max. 90 min am Stück (dann 15 min Pause), unplausibel < 10 °C, > 50 °C, > 3 °C Sprung
  in 10 s oder > 30 s ohne Wert. Nie direkt `SockDevice.setHeat` aus UI/ViewModel aufrufen,
  sondern immer `HeatController`.
- Änderungen an der Sicherheitslogik nur mit Tests, die die Grenze treffen (knapp darunter
  und knapp darüber).
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

- Unit-Tests unter `app/src/test` (JUnit4, Truth, coroutines-test, Turbine). Hilfen in
  `testing/` (`MutableClock`, `FakeSockDevice`, `waitUntilDisplayed`).
- Simulator-Tests laufen in virtueller Zeit (`runTest`, `backgroundScope` als App-Scope).
- Room-Tests mit In-Memory-Datenbank unter Robolectric. Jede neue Migration bekommt einen
  Fall in `MigrationTest` (alte DB wird aus `app/schemas/…/N.json` erzeugt).
- Regler-Tests mit `FakeSockDevice`/`FakePairProvider` und `SchedulerClock` (virtuelle Zeit).
- UI-Tests ebenfalls unter `app/src/test` mit Robolectric (`@HiltAndroidTest`,
  `@Config(application = HiltTestApplication::class)`), Compose-Test-API v2.
  Robolectric läuft mit SDK 36 (`src/test/resources/robolectric.properties`).
  Nach Navigation nicht sofort `assertIsDisplayed`, sondern `waitUntilDisplayed(tag)`
  (Übergangsanimation). Vor dem ersten Klick `waitUntilLoaded()` (kein `state_loading`
  mehr sichtbar). `waitUntil` immer mit `UI_TIMEOUT_MS` – CI-Runner sind langsam.
  Vor Klicks auf Elemente, die weggescrollt sein können, `performScrollTo()`.
- Algorithmen (Schlafphasen, Score, Routinen, Sicherheit) brauchen eigene Unit-Tests.

## Git

- Ein Commit pro Phase bzw. abgeschlossenem Schritt, Nachricht auf Deutsch.
- README-Abschnitt „Stand der Phasen“ bei jedem Phasenabschluss aktualisieren.

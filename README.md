# Zocks Zleep

*Zleep well, zleep Zocks.*

Begleit-App für die smarten Schlafsocken **Zocks**: Fußmassage, Heizen und Schlaftracking
über einen optischen Sensor (PPG). Die App steuert beide Socken per Bluetooth Low Energy,
zeichnet die Nacht auf und zeigt eine übersichtliche Schlafanalyse.

> Zocks Zleep ist ein **Wellnessprodukt, kein Medizinprodukt**. Alle Werte sind Schätzungen.

<p>
  <img src="docs/screenshots/home.png" width="240" alt="Startseite">
  <img src="docs/screenshots/steuerung.png" width="240" alt="Steuerung">
  <img src="docs/screenshots/einstellungen.png" width="240" alt="Einstellungen">
</p>

## Grundsätze

- **Offline-first:** Alle Daten bleiben auf dem Gerät. Kein Konto, keine Cloud, keine Backups.
- **Dunkles Design als Standard:** tiefes Nachtblau, warmer Akzent für Wärme, kühler für Schlaf.
- **Daumentauglich:** Bedienelemente für Heizen und Massage sind mindestens 64 dp hoch.
- **Keine erfundenen Messwerte:** Was der Sensor nicht liefert, wird als „nicht verfügbar“ gezeigt.
- **Sprache:** Deutsch (Standard), Englisch als zweite Sprache.

## Technik

| Bereich | Wahl |
|---|---|
| Sprache / UI | Kotlin 2.4, Jetpack Compose, Material 3, Single-Activity |
| Navigation | Navigation Compose mit typsicheren Routen (`kotlinx.serialization`) |
| Architektur | MVVM, Schichten `ui / domain / data / device` |
| DI | Hilt |
| Asynchronität | Coroutines + Flow |
| Persistenz | Room (Messdaten, Nächte), DataStore (Einstellungen) |
| Diagramme | Vico *(ab Phase 5)* |
| SDK | minSdk 26, targetSdk / compileSdk 37 |
| Build | Gradle 9.8, AGP 9.4, Version Catalog `gradle/libs.versions.toml` |

## Aufbau

```
app/src/main/java/at/zocks/zleep/
├── ZocksApplication.kt      Hilt-Einstieg
├── MainActivity.kt          einzige Activity, Splash, Edge-to-Edge
├── ui/                      Compose-Screens, ViewModels, Theme, Navigation
│   ├── theme/               Farben, Typografie, Abstände, ZocksTheme
│   ├── navigation/          typsichere Routen, Ziele der unteren Leiste
│   ├── components/          Karten, große Aktionsknöpfe, Lade-/Leer-/Fehlerzustände
│   ├── home/ nights/ control/ settings/
│   ├── developer/           Entwickleroptionen (Simulator, Demo-Daten)
│   ├── nightdetail/         Nachtdetail
│   └── format/              Zahlen, Zeiten, Einheiten, Übersetzungen
├── domain/                  reines Kotlin
│   ├── model/               Nacht, Epochen, Schlafphasen, Geräte-Modelle, Einstellungen
│   ├── device/              SockDevice, SockPair, SockPairProvider
│   ├── repository/          Repository-Interfaces
│   ├── simulator/           Steuerung von Simulator und Demo-Daten
│   └── analysis/            Kennzahlen einer Nacht
├── data/                    Room (db/), Repositories, DataStore (settings/)
├── device/                  Simulator (simulator/), BLE (ble/, ab Phase 7)
└── di/                      Hilt-Module
```

Die Domain-Schicht darf nichts aus Android, `ui`, `data` oder `device` importieren.
`DomainPurityTest` prüft das bei jedem Testlauf.

## Bauen und testen

Voraussetzungen: JDK 21, Android SDK mit Plattform 37 (`platforms;android-37.0`).

```bash
./gradlew assembleDebug        # Debug-APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit- und UI-Tests (Robolectric, ohne Emulator)
./gradlew lintDebug            # Android Lint
```

Die Debug-App hat die ID `at.zocks.zleep.debug` und kann neben einer späteren
Release-Version installiert sein. Der Debug-Schlüssel liegt im Repo (`app/debug.keystore`),
damit sich jede CI-APK über die vorherige installieren lässt.

## APK aufs Handy laden

Jeder Push startet den Workflow **CI** (`.github/workflows/ci.yml`): bauen, testen, Lint.
Danach liegt die Debug-APK unter *Actions → letzter Lauf → Artifacts →
`zocks-zleep-debug-apk`*. ZIP herunterladen, entpacken, APK auf dem Handy öffnen
(„Installation aus unbekannten Quellen“ einmalig erlauben).

## Stand der Phasen

| Phase | Inhalt | Stand |
|---|---|---|
| 1 | Projektgerüst, Theme, Navigation, CI | ✅ fertig |
| 2 | Datenmodell, Room, Simulator mit Demo-Nächten | ✅ fertig |
| 3 | Gerätesteuerung: Heizen und Massage | ⏳ offen |
| 4 | Nachtaufzeichnung mit Foreground Service | ⏳ offen |
| 5 | Analyse, Score, Diagramme, Trends | ⏳ offen |
| 6 | Abendroutine, smarter Wecker, Health Connect, Export | ⏳ offen |
| 7 | BLE-Implementierung, Feinschliff, Tests | ⏳ offen |

### Phase 1 – enthalten

- Gradle-Projekt mit Version Catalog, Hilt, KSP, Compose, Navigation, Splash Screen
- Theme „Nacht“ (dunkel als Standard) mit fachlichen Farben für Wärme, Schlaf, Massage
  und Schlafphasen (`ZocksThemeExt.colors`)
- Untere Navigation: Start, Nächte, Steuerung, Einstellungen
- Startseite mit Leerzustand „letzte Nacht“, Sockenstatus links/rechts, Schnellzugriff
- Steuerung mit Reitern Heizen/Massage (Schnellzugriff öffnet direkt den passenden Reiter)
- Einstellungen mit Hinweis „Wellnessprodukt, kein Medizinprodukt“
- Logo als Vektor (Socke mit „zzz“), adaptives Launcher-Icon inkl. Monochrom-Variante
- Wiederverwendbare Lade-, Leer- und Fehlerzustände
- Texte in Deutsch und Englisch, App-Sprache pro App wählbar (Android 13+)
- Keine Cloud-Backups (`data_extraction_rules.xml`)
- Tests: Domain-Reinheit, Begrüßungslogik, Navigations-UI-Tests (Hilt + Robolectric)
- CI mit Debug-APK als Artefakt

### Phase 2 – enthalten

- **Datenmodell:** Nächte, 30-s-Epochen je Socke (Puls, HRV, SpO2, Hauttemperatur,
  Bewegung – jeweils `null`, wenn nicht gemessen), Schlafphasen, Ereignisse (Wärme,
  Massage, Routine, Wecker, Sicherheitsabschaltung), Verbindungslücken, Tags, Notizen.
- **Room** mit Schema-Export, eingebaute Tags (Koffein, Sport, Alkohol, Stress, spätes
  Essen, Bildschirmzeit), **DataStore** für Einstellungen.
- **Geräteschicht:** `SockDevice` je Socke, `SockPair` (gemeinsam oder getrennt),
  `SockPairProvider` wählt Simulator oder echtes Gerät.
- **Simulator:**
  - Nachtgenerator mit Schlafzyklen von 85–110 min (Tiefschlaf früh, REM spät) und
    phasentypischem Puls, HRV, SpO2, Fußtemperatur und Bewegung.
  - `SimulationEngine` in Echtzeit oder als Zeitraffer-Nacht (60×, 300×, 1200×).
  - `SimulatedSockDevice` reagiert auf Heizen (Heizelement und Haut erwärmen sich),
    Massage (Vibration, sanftes Ausklingen), verbraucht Akku und simuliert Verbindungsabbrüche.
- **Demo-Datensatz:** 30 Nächte mit Tags, Wärme- und Massage-Ereignissen und
  gelegentlichen Lücken. Eingebaute Zusammenhänge (z. B. Koffein → längeres Einschlafen)
  machen spätere Trends und Erkenntnisse sichtbar.
- **UI:**
  - Start zeigt die letzte Nacht und den echten Sockenstatus (Verbinden/Trennen).
  - Nächte als Liste, einfache Nachtdetailseite mit Kennzahlen.
  - Entwickleroptionen mit Simulator-Umschalter, Live-Werten, Zeitraffer-Nacht und
    Demo-Daten.
- **Tests (49):** Nachtgenerator, simulierte Socke, Engine, Demo-Daten, Room-Repository,
  DataStore, Kennzahlen, `SockPair`, UI-Abläufe (Verbinden, Demo laden → Nacht öffnen,
  Gerätemodus wechseln).

### Simulator ausprobieren

*Einstellungen → Entwickleroptionen:*
1. „Socken verbinden“ (Live-Werte erscheinen),
2. „Nacht abspielen“ (Zeitraffer wählen),
3. „30 Demo-Nächte laden“ (danach unter *Nächte*).

Bis die Bluetooth-Anbindung steht (Phase 7), ist der Simulator Standard.

## Hardware-Annahmen

- Jede Socke hat ein eigenes, abnehmbares Elektronikmodul (ESP32, BLE) → zwei BLE-Geräte pro Paar.
- Vibrationsmotoren an Ferse, Fußgewölbe, Ballen und Zehen; Heizpads im vorderen Fußbereich.
- Zieltemperatur 20–40 °C. Die Sicherheitslogik der App ersetzt **keine** Absicherung in der Firmware.

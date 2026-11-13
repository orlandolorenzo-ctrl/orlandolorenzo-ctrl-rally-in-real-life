# Rally in Real Life 🏁

App Android che trasforma la navigazione Google Maps in un copilota da rally:
calcola il percorso con la **Google Directions API** e lo converte in **note
copilota in stile roadbook** (sistema numerico ufficiale 1–6 usato nei rally),
lette a voce alta come un vero co-pilota, con supporto **Android Auto**.

## Icona e splash screen

- **Icona**: auto rally stile Subaru (carrozzeria blu, cerchi oro, spoilone)
  su sfondo notturno con fango — vector drawable adattivo.
- **Splash screen**: la stessa auto in versione disegno animato che **drifta
  nel fango**, con spruzzi di fango dietro le ruote posteriori e strisciate
  sul terreno (`DriftSplashView`, animazione Canvas in loop).

## Il sistema di note (regolamento roadbook)

Convenzione numerica standard adottata nei libretti delle note dei rally:

| Numero | Curva |
|--------|-------|
| 1 | Strettissima / tornante (quasi fermo) |
| 2 | Molto stretta |
| 3 | Stretta |
| 4 | Media |
| 5 | Veloce |
| 6 | Molto veloce, quasi in linea |

Modificatori: `si stringe`, `si apre`, `lunga`, `corta`, `doppia`,
`attenzione`, `tornante`, `rotonda`, `immissione`, `traghetto`.

Esempio di chiamata del copilota:
> "destra 3, si stringe, per 200 metri"

La severità è calcolata dal reale cambio di direzione (bearing) della strada
più il tipo di manovra segnalato da Google (`sharp-right`, `slight-left`,
`uturn`, `roundabout`, …).

## Requisiti

1. Una **Google Maps API Key** con abilitati *Maps SDK for Android* e
   *Directions API* ([console Google Cloud](https://console.cloud.google.com/)).
2. Nel file `~/.gradle/gradle.properties` (globale, fuori dal repository) o in
   un file `secrets.properties` locale aggiungere:
   ```properties
   MAPS_API_KEY=LA_TUA_CHIAVE
   ```
   In alternativa sostituire il placeholder in `app/build.gradle.kts`.

## Compilazione

Aprire la cartella in **Android Studio** (scarica Gradle 8.7 automaticamente
tramite il wrapper) e premere Run su un dispositivo con Google Play
Services. All'avvio concedere il permesso della posizione.

Da riga di comando, con Gradle installato:

```bash
git clone <questo-repo> && cd RallyInRealLife
./gradlew :app:assembleDebug
```

## Android Auto

- L'app dichiara la categoria `androidx.car.app.category.NAVIGATION`
  (`RallyCarService` con la libreria `androidx.car.app`).
- Avvia il rally sul telefono: la nota corrente e le prossime 3 note
  compaiono sul display dell'auto (`RallyAutoScreen`).
- Nota: per la pubblicazione su Play Store come app di navigazione su
  Android Auto serve l'approvazione di Google; per l'uso personale
  (debug / Android Auto for developers) funziona subito.

## Struttura

```
app/src/main/java/com/rallyinreallife/app/
├── MainActivity.kt            # mappa, percorso, note in tempo reale
├── nav/DirectionsService.kt   # Google Directions API + decoder polyline
├── nav/NavigationTracker.kt   # GPS: fa scorrere le note lungo il percorso
├── nav/RallySession.kt        # stato condiviso telefono <-> Android Auto
├── pace/PaceNoteEngine.kt     # note copilota (sistema 1-6 + modificatori)
├── tts/CoDriverTts.kt         # voce del copilota (TTS italiano)
├── auto/RallyCarService.kt    # servizio Android Auto
├── auto/RallyAutoScreen.kt    # schermata sul display dell'auto
└── ui/DriftSplashView.kt      # splash: auto che drifta nel fango
```

## Avvertenza

Guida sempre su strada pubblica rispettando il codice della strada: le note
sono un omaggio al mondo rally, non un invito a correre. 🚗💨

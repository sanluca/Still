# Still - Minimal Zen Timer

[🇮🇹 Versione Italiana](#-versione-italiana) | [🇬🇧 English Version](#-english-version)

---

# 🇮🇹 Versione Italiana

## Cos'è Still?
Still è un'applicazione open source per Android e Wear OS, concepita per la meditazione (Mindfulness e Vipassana). A differenza di molti altri timer sul mercato, si concentra sul minimalismo assoluto, sull'affidabilità del conteggio a schermo spento e sull'integrazione nativa e trasparente con **Google Salute (Health Connect)**.

---

## Come funziona l'App

### 📱 App Smartphone (Android)
- **Quadrante & Selezione Rapida**: Scegli il tempo dai preset immediati (`5m`, `10m`, `15m`, `20m`) oppure seleziona **∞ (Infinito)** per una sessione libera senza limiti di tempo.
- **Pulsanti di Regolazione Fine**: Aggiungi o togli tempo al timer pre-esistente con precisione tramite i pulsanti `-5m`, `-1m`, `+1m` e `+5m`.
- **Ritardo Iniziale (Preparazione)**: Puoi impostare un tempo di preparazione in secondi dalle impostazioni: Still mostrerà un conto alla rovescia dedicato (*"In preparazione"*) prima di far suonare il gong di inizio sessione.
- **Gong Audio in Tempo Reale**: Il suono del gong viene sintetizzato matematicamente in tempo reale tramite `AudioTrack` su frequenze armoniche selezionabili (**432 Hz**, **528 Hz**, **216 Hz**), senza ricorrere a file MP3 compressi.
- **Campana Intermedia**: Se attivata dalle impostazioni (es. ogni 10 o 15 minuti), un gentile tocco del gong suonerà periodicamente in background per richiamare la presenza mentale durante le sessioni più lunghe.
- **Non Disturbare Automatico (DND)**: Se abilitato e autorizzato nelle impostazioni di sistema, Still attiva automaticamente la modalità Non Disturbare non appena premi *'Inizia'*, ripristinando lo stato audio precedente quando premi *'Pausa'* o *'Salva'*.
- **Overtime (Minuti Extra ✨)**: Al raggiungimento del tempo previsto suonano 3 rintocchi finali. Il timer non si arresta forzatamente, ma continua a conteggiare i minuti extra (Overtime), consentendoti di decidere se salvare solo il tempo pianificato oppure includere i minuti extra.
- **Foreground Service & WakeLock**: All'avvio del timer, l'app avvia un *Foreground Service* (`StillTimerService`, tipo `mediaPlayback`) abbinato a un `PowerManager.WakeLock`. Puoi tranquillamente **spegnere lo schermo** o passare ad altre app: il conteggio prosegue in modo rigoroso e affidabile basandosi su `SystemClock.elapsedRealtime()`.
- **Salvataggio & Storico Locale**: Alla fine di ogni sessione, Still registra l'evento nello **Storico Locale (📊)** (con possibilità di eliminare le singole voci) e lo sincronizza con Google Salute.
- **Coda Offline**: Se Health Connect non è ancora configurato o autorizzato al momento del salvataggio, le sessioni vengono trattenute in una coda locale e sincronizzate automaticamente alla prima apertura utile.

### ⌚ App Orologio (Wear OS)
- **Interfaccia Ottimizzata per Schermi Circolari**: Visualizzazione a scorrimento (`ScalingLazyColumn`) con anello di avanzamento e pulsanti `[-]` e `[+]` per regolare la durata.
- **Feedback Aptico & Audio**: Rintocco con vibrazione aptica dell'orologio (`triggerHapticGong`) sincronizzato al gong audio (1 rintocco all'inizio, 3 rintocchi all'inizio dell'overtime).
- **Impostazioni da Polso**: Scorrendo verso il basso puoi scegliere la frequenza del gong (432Hz, 528Hz, 216Hz) e regolare i secondi di preparazione (a step di ±5 secondi).
- **Modalità Ambient & Risparmio Energetico**:
  - L'Activity implementa `AmbientModeSupport`: quando abbassi il polso o dopo qualche secondo di inattività, lo schermo entra in **Modalità Ambient** (Always-on a 1 Hz, sfondo nero puro `Color.Black` a contrasto per preservare l'autonomia della batteria e prevenire il burn-in del pannello OLED).
  - Il servizio di background dell'orologio (`StillTimerService`) sfrutta `PowerManager.PARTIAL_WAKE_LOCK`: il timer continua a calcolare il tempo esatto, vibrando e suonando anche a schermo oscurato o spento.
- **Scheda Rapida (Tile)**: Include una scheda rapida Wear OS (`StillTileService`) posizionabile nella giostra delle schermate dell'orologio per aprire l'app con un solo tocco.
- **Sincronizzazione Bluetooth**: L'orologio comunica in background con lo smartphone tramite `Wearable Data Layer` (Google Play Services): le impostazioni impostate sul telefono vengono riflesse sull'orologio, e le sessioni concluse sull'orologio vengono recapitate al telefono per la registrazione locale e su Health Connect.
- **Backup Offline sull'Orologio**: Se mediti senza telefono a portata (es. all'aperto o in modalità aereo), le sessioni rimangono memorizzate nella cache dell'orologio e vengono riversate sullo smartphone non appena i dispositivi si riconnettono.

---

## 🔒 Integrazione con Google Salute (Health Connect) & Privacy

Still adotta un approccio rigoroso e trasparente alla tutela dei dati personali:

- **Mindfulness Nativa (Non come Esercizio)**: Le sessioni vengono salvate come record nativi `MindfulnessSessionRecord` con tipologia `MINDFULNESS_SESSION_TYPE_MEDITATION`, titolo *"Still"* e metadati di registrazione attiva (`Metadata.activelyRecorded`). Compaiono regolarmente nella sezione **Mindfulness / Benessere mentale** di Google Salute, distinte dagli allenamenti sportivi.
- **Permessi Minimi di Sola Scrittura**: Still richiede unicamente il permesso di scrittura `WRITE_MINDFULNESS` (`androidx.health.permission.Mindfulness.WRITE` su Android 13 e `android.permission.health.WRITE_MINDFULNESS` su Android 14+). L'app **non richiede né possiede permessi di lettura** per altri dati sanitari (battito cardiaco, sonno, passi, ecc.).
- **Informativa sui Permessi di Sistema**: Supporta gli intent nativi di spiegazione permessi per Android 13 (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) e per Android 14+ (`android.intent.action.VIEW_PERMISSION_USAGE` protetto da `START_VIEW_PERMISSION_USAGE`).
- **Nessun Server, Nessun Account, Zero Pubblicità**: Still funziona interamente offline sul dispositivo; non richiede login e non trasmette informazioni a server esterni o società terze.
- L'informativa completa è consultabile nel file [PRIVACY.md](PRIVACY.md).

---

## 🛠️ Scelte Tecniche e Architettura
- **UI Dichiarativa**: Realizzata interamente con **Jetpack Compose** (smartphone) e **Wear Compose Material 3** con `ScalingLazyColumn` (smartwatch).
- **Health Connect Client**: Utilizza le API ufficiali `androidx.health.connect.client.HealthConnectClient`.
- **Servizi in Background e Affidabilità**:
  - Smartphone: `ForegroundService` con tipo `mediaPlayback` e `PARTIAL_WAKE_LOCK` dinamico calibrato sulla durata della sessione.
  - Orologio: `ForegroundService` con tipo `specialUse` (dichiarato con proprietà `Meditation timer and background chime`) per garantire l'immunità da kill del sistema operativo e il risveglio automatico della schermata di salvataggio a fine sessione.
- **Sintesi Sonora**: Generazione audio procedurale tramite campionamento sinusoidale a 44.1 kHz riprodotto istantaneamente con `AudioTrack` senza dipendenze da risorse audio esterne.
- **Lingue Supportate**: Italiano e Inglese (sia su smartphone che su smartwatch).
- **Requisiti di Sistema**:
  - **Smartphone**: Android 8.0+ (`minSdk 26`).
  - **Smartwatch**: Wear OS 2.0+ / Google Pixel Watch (`minSdk 28`).

---

## 📄 Licenza (GNU GPLv3)

Copyright (C) 2026 **sanluca**

Questo progetto è software libero e gratuito, rilasciato sotto licenza **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.

Sei libero di usare, studiare, condividere e modificare il codice a condizione di:
1. Mantenere l'attribuzione e il copyright dell'autore originale (**`sanluca`**).
2. Indicare chiaramente le eventuali modifiche apportate.
3. Distribuire qualsiasi versione modificata o derivata gratuitamente con codice sorgente aperto sotto la stessa licenza **GPLv3**.

💬 **Nota dell'autore:** Se utilizzi, modifichi o migliori questo progetto, **fammelo sapere** aprendo una *Issue* o una *Pull Request* qui su GitHub!

---

# 🇬🇧 English Version

## What is Still?
**Still** is an open-source meditation timer for Android smartphones and Wear OS smartwatches (Mindfulness and Vipassana). Unlike bloated commercial apps, Still focuses on absolute minimalism, reliable screen-off background timing, and native, privacy-first integration with **Google Health Connect**.

---

## How It Works

### 📱 Smartphone App (Android)
- **Quick Presets & Open Mode**: Choose from instant presets (`5m`, `10m`, `15m`, `20m`) or select **∞ (Unlimited)** for an open-ended meditation session.
- **Fine Duration Controls**: Adjust the timer with precision using `-5m`, `-1m`, `+1m`, and `+5m` buttons.
- **Preparation Delay**: Set a custom preparation countdown in seconds before the opening gong sounds.
- **Real-Time Procedural Gong Synthesis**: Tibetan bowl / gong chimes are mathematically synthesized in real time via `AudioTrack` at selectable harmonic frequencies (**432 Hz**, **528 Hz**, **216 Hz**), with zero compressed MP3 files.
- **Intermediate Bell**: Optionally play a gentle mindfulness chime every X minutes during longer sessions.
- **Automatic Do Not Disturb (DND)**: When enabled and authorized in system settings, Still automatically activates Do Not Disturb when you press *Start* and restores your previous interruption filter when you *Pause* or *Save*.
- **Overtime (Extra Minutes ✨)**: When the planned time finishes, 3 closing gongs chime. Instead of abruptly stopping, the timer counts extra minutes (Overtime), letting you save either the planned duration or include the extra time.
- **Foreground Service & Dynamic WakeLock**: Uses a `mediaPlayback` Foreground Service (`StillTimerService`) paired with a dynamic `PowerManager.PARTIAL_WAKE_LOCK` and `SystemClock.elapsedRealtime()` so the timer and chimes remain accurate even with the screen off.
- **Local History & Offline Queue**: Sessions are logged locally (**📊**) and synced to Google Health Connect (or queued locally if Health Connect is not yet configured).

### ⌚ Smartwatch App (Wear OS)
- **Circular Display UI**: Built with `ScalingLazyColumn`, circular progress ring, and `[-]` / `[+]` duration controls.
- **Haptic & Audio Chimes**: Synchronized wrist vibration (`triggerHapticGong`) and speaker gong (1 chime at start, 3 chimes at completion, plus automatic wake-up of the save screen).
- **On-Wrist Settings**: Scroll down to select the gong frequency (`432Hz`, `528Hz`, `216Hz`) and preparation delay (±5s steps).
- **Ambient Mode & Battery Efficiency**: Supports `AmbientModeSupport` (pure black OLED background in Always-On mode) and a dynamic `PARTIAL_WAKE_LOCK` inside `StillTimerService`.
- **Quick Launch Tile**: Includes a Wear OS Tile (`StillTileService`) to open the timer with a single tap.
- **Bluetooth Sync & Offline Backup**: Communicates with the phone via Google Play Services `Wearable Data Layer` (`DataClient`), queuing completed watch sessions offline if the phone is out of reach and syncing automatically upon reconnection.

---

## 🔒 Google Health Connect Integration & Privacy
- **Native Mindfulness Records**: Completed sessions are written as native `MindfulnessSessionRecord` entries (`MINDFULNESS_SESSION_TYPE_MEDITATION`, title *"Still"*, `Metadata.activelyRecorded`).
- **Write-Only Permission**: Still requests **only** `WRITE_MINDFULNESS` (`androidx.health.permission.Mindfulness.WRITE` on Android 13 and `android.permission.health.WRITE_MINDFULNESS` on Android 14+). It **never reads** any health or personal data.
- **No Servers, No Accounts, Zero Ads**: 100% offline, no analytics, no trackers.
- Full policy available in [PRIVACY.md](PRIVACY.md).

---

## 🛠️ Technical Architecture & Requirements
- **UI**: Jetpack Compose (Phone) & Wear Compose Material 3 (Watch).
- **Languages Supported**: English and Italian (both Phone and Watch).
- **System Requirements**:
  - **Smartphone**: Android 8.0+ (`minSdk 26`).
  - **Smartwatch**: Wear OS 2.0+ / Google Pixel Watch (`minSdk 28`).

---

## 📄 License (GNU GPLv3)

Copyright (C) 2026 **sanluca**

This project is free and open-source software licensed under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.

You are free to use, study, share, and modify the source code provided that you:
1. Retain the original copyright and attribution (**`sanluca`**).
2. Clearly state any changes made to the code.
3. Distribute any modified or derivative versions free of charge and open source under the same **GPLv3** license.

💬 **Author's note:** If you use, modify, or improve this project, **please let me know** by opening an *Issue* or a *Pull Request* on GitHub!



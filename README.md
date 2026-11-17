# Still - Minimal Zen Timer (v2.0.3)

[🇮🇹 Versione Italiana](#-versione-italiana) | [🇬🇧 English Version](#-english-version)

---

# 🇮🇹 Versione Italiana

## Cos'è Still?
Still è un'applicazione open source per Android e Wear OS, concepita per la meditazione (Mindfulness e Vipassana). A differenza di molti altri timer sul mercato, si concentra sul minimalismo assoluto, sull'affidabilità del conteggio a schermo spento o attivo e sull'integrazione nativa e trasparente con **Google Salute (Health Connect)**.

---

## Novità della Versione 2.0.3
- **4 Timbri di Campana Sintetizzati in PCM a 16-bit (Anti-Click)**: Sintesi procedurale con inviluppo cosinusoidale morbido (*raised-cosine*) privo di click elettrici: **Gong** (216 Hz), **Quarzo** (432 Hz), **Keisu** (528 Hz) e **Tingsha** (2640 Hz, cimbali tibetani cristallini con armoniche metalliche inarmoniche e battimento acustico).
- **Campana Intermedia Indipendente**: Su smartphone puoi ora scegliere un timbro dedicato per i rintocchi intermedi (predefinito su **Tingsha**), distinto dalla campana principale di apertura e chiusura sessione.
- **Selettore 2×2 su Wear OS**: Supporto completo a tutti e 4 i timbri (*Quarzo*, *Keisu*, *Gong*, *Tingsha*) anche sullo smartwatch con griglia 2×2 ottimizzata per display rotondi.
- **3 Visualizzatori Grafici Nativi in Jetpack Compose Canvas**:
  - **Ruota Zen**: Geometrie tangenziali discrete in controluce e cursore a brace d'incenso con bagliore radiale additivo (`BlendMode.Plus`) e scia luminosa.
  - **Clessidra 3D**: Fisica della sabbia con concavità a imbuto superiore, cumulo conico inferiore (~30°), inerzia elastica (`spring`) e 40 granelli indipendenti a caduta lenta (1.8s–2.6s) che si sparpagliano a ventaglio e scivolano lungo il pendio.
  - **Candela 3D**: Laghetto concavo di cera fusa, colature asimmetriche con riflessi laterali, stoppino curvo con brace incandescente, fiamma organica a 3 sinusoidi incommensurabili (con `RuntimeShader` AGSL e turbolenza FBM su Android 13+ e fallback Canvas automatico) e micro-scintille ascendenti.
- **4 Temi Stagionali OLED**: Tutti i visualizzatori e l'interfaccia si adattano dinamicamente alle 4 palette stagionali (**Primavera**, **Estate**, **Autunno**, **Inverno**).

---

## Come funziona l'App

### 📱 App Smartphone (Android)
- **Triplo Stile Visivo (Ruota Zen, Clessidra & Candela)**: Scegli nelle impostazioni tra la **Ruota Zen** con cursore luminoso diffuso, la **Clessidra** animata (con fisica a imbuto/cono e caduta lenta sparpagliata dei granelli) e la **Candela** (con cera fusa, stoppino curvo e fiamma viva che si consuma gradualmente fino al residuo finale), tutti ottimizzati a ~40 fps e a zero allocazioni per frame nel `DrawScope`. Tocca il timer in corso per nascondere o mostrare le cifre.
- **4 Temi Stagionali OLED**: Scegli nelle impostazioni tra **Primavera**, **Estate**, **Autunno** e **Inverno**, calibrati su sfondo nero OLED per ridurre l'affaticamento visivo e il consumo energetico.
- **Regolazione del Tempo (Anche a Sessione Avviata)**: Imposta la durata o seleziona **∞ (Infinito)** per una sessione senza limiti. Puoi usare i pulsanti `-5m`, `-1m`, `+1m` e `+5m` sia prima di partire sia **in tempo reale durante la meditazione** (o in pausa) per allungare o accorciare la sessione senza interromperla, con indicazione del tempo iniziale sotto il timer.
- **Ritardo Iniziale (Preparazione)**: Puoi impostare un tempo di preparazione in secondi dalle impostazioni: Still mostrerà un conto alla rovescia dedicato (*"Preparati"*) prima di far suonare il gong di inizio sessione.
- **4 Campane Sintetizzate in Tempo Reale & Modalità Avviso**: Il suono viene sintetizzato matematicamente in tempo reale in PCM a 16-bit (44.1 kHz) tramite `AudioTrack` con inviluppo anti-click su 4 timbri selezionabili (**Gong** a 216 Hz, **Quarzo** a 432 Hz, **Keisu** a 528 Hz, **Tingsha** a 2640 Hz), con regolazione del volume (20%–100%) e scelta della modalità di avviso (*Entrambi*, *Campane*, *Vibrazione*).
- **Campana Intermedia con Timbro Dedicato**: Se attivata dalle impostazioni (es. ogni 5, 10 o 15 minuti), compare un selettore dedicato per scegliere il timbro specifico del richiamo intermedio (predefinito su **Tingsha**), indipendente dal timbro di inizio/fine meditazione.
- **Non Disturbare Automatico (DND)**: Se abilitato e autorizzato nelle impostazioni di sistema, Still attiva automaticamente la modalità Non Disturbare non appena premi *'Inizia'*, ripristinando lo stato audio precedente quando premi *'Pausa'* o *'Salva'*.
- **Overtime (Minuti Extra ✨)**: Al raggiungimento del tempo previsto suonano 3 rintocchi finali della campana principale. Il timer non si arresta forzatamente, ma continua a conteggiare i minuti extra (Overtime), consentendoti di decidere se salvare solo il tempo pianificato oppure includere i minuti extra.
- **Schermo Attivo & Foreground Service con WakeLock**: Durante la sessione lo schermo rimane attivo (`FLAG_KEEP_SCREEN_ON`), mentre un *Foreground Service* (`StillTimerService`, tipo `mediaPlayback`) abbinato a un `PowerManager.PARTIAL_WAKE_LOCK` garantisce che, anche se spegni manualmente lo schermo o passi ad altre app, il conteggio e i rintocchi proseguano in modo rigoroso basandosi su `SystemClock.elapsedRealtime()`.
- **Salvataggio & Storico Locale**: Alla fine di ogni sessione, Still registra sempre l'evento nello **Storico Locale (`≡`)** (con statistiche, serie di giorni consecutivi e possibilità di eliminare le singole voci) e, se collegato, lo sincronizza con Google Salute senza duplicati.
- **Coda di Sincronizzazione & Risincronizzazione Storico**: Se Google Salute è collegato ma temporaneamente non disponibile, la sessione viene accodata e inviata automaticamente al rientro. Se invece colleghi Google Salute in un secondo momento, puoi inviare tutto lo storico pregresso con un tocco tramite il pulsante **"Risincronizza storico"** nelle impostazioni.
- **Aggiornamenti In-App (Play Store)**: All'apertura (solo quando nessuna meditazione è in corso), l'app verifica in modo silenzioso la presenza di aggiornamenti su Google Play proponendo il download flessibile in background.

### ⌚ App Orologio (Wear OS)
- **Interfaccia Ottimizzata per Schermi Circolari**: Navigazione fluida a pagine (`VerticalPager` e `HorizontalPager`) con anello di avanzamento, pulsanti `[-]` / `[+]` e supporto alla **corona rotante / ghiera** per regolare la durata.
- **Feedback Aptico & Audio**: Rintocco con vibrazione aptica dell'orologio (`triggerHapticGong`) sincronizzato al gong audio (1 rintocco all'inizio, 3 rintocchi all'inizio dell'overtime).
- **Impostazioni da Polso con Selettore 2×2**: Scorrendo verso il basso accedi alla configurazione di **Modalità avviso** (*Entrambi*, *Campane*, *Vibraz.*), **Timbro della campana** con griglia 2×2 ottimizzata per schermi rotondi (**Quarzo** 432 Hz, **Keisu** 528 Hz, **Gong** 216 Hz, **Tingsha** 2640 Hz con anteprima al tocco), **Volume della campana** (20%–100%) e **Tema stagionale** (*Primavera*, *Estate*, *Autunno*, *Inverno*).
- **Modalità Ambient & Risparmio Energetico**:
  - L'Activity implementa `AmbientModeSupport`: quando abbassi il polso, lo schermo entra in **Modalità Ambient** (Always-on a 1 Hz, sfondo nero puro `Color.Black` a contrasto per preservare l'autonomia della batteria e prevenire il burn-in del pannello OLED).
  - Il servizio di background dell'orologio (`StillTimerService`) sfrutta `PowerManager.PARTIAL_WAKE_LOCK`: il timer continua a calcolare il tempo esatto, vibrando e suonando anche a schermo oscurato o spento.
- **Scheda Rapida (Tile)**: Include una scheda rapida Wear OS (`StillTileService`) posizionabile nella giostra delle schermate dell'orologio per aprire l'app con un solo tocco.

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
- **Versione**: `2.0.3`
- **UI Dichiarativa & Grafica Procedurale**: Realizzata interamente con **Jetpack Compose Material 3** e `Canvas` nativo (con supporto opzionale a `RuntimeShader` AGSL su Android 13+ per la fiamma della candela e loop `withFrameNanos` limitati a ~40 fps che si arrestano automaticamente in pausa/stop).
- **Health Connect Client**: Utilizza le API ufficiali `androidx.health.connect.client.HealthConnectClient`.
- **Servizi in Background e Affidabilità**:
  - Smartphone: `ForegroundService` con tipo `mediaPlayback` e `PARTIAL_WAKE_LOCK` dinamico calibrato sulla durata della sessione.
  - Orologio: `ForegroundService` con tipo `specialUse` (dichiarato con proprietà `Meditation timer and background chime`) per garantire l'immunità da kill del sistema operativo e il risveglio automatico della schermata di salvataggio a fine sessione.
- **Sintesi Sonora PCM a 16-bit**: Generazione audio procedurale a 44.1 kHz con inviluppo anti-click (*fade-in* / *fade-out* cosinusoidale) riprodotto tramite `AudioTrack` per tutti e 4 i timbri (**Gong**, **Quarzo**, **Keisu**, **Tingsha**) senza dipendenze da file audio esterni.
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
**Still** is an open-source meditation timer for Android smartphones and Wear OS smartwatches (Mindfulness and Vipassana). Unlike bloated commercial apps, Still focuses on absolute minimalism, reliable background timing, and native, privacy-first integration with **Google Health Connect**.

---

## What's New in Version 2.0.3
- **4 Bell Timbres Synthesized in 16-bit PCM (Anti-Click Envelope)**: Real-time procedural synthesis with smooth raised-cosine fade-in/fade-out envelopes: **Gong** (216 Hz), **Crystal** (432 Hz), **Keisu** (528 Hz), and **Tingsha** (2640 Hz Tibetan cymbals with crystalline inharmonic metallic partials and acoustic beating).
- **Independent Intermediate Bell Selector**: On smartphones, choose a dedicated timbre for intermediate interval chimes (defaulting to **Tingsha**), completely independent from the main start/end bell.
- **Wear OS 2×2 Bell Selector**: Full support for all 4 bell timbres (*Crystal*, *Keisu*, *Gong*, *Tingsha*) on smartwatches via a compact 2×2 grid tailored for round screens.
- **3 Native Jetpack Compose Canvas Visualizers**:
  - **Zen Wheel**: Subtle backlit internal geometry with a diffused incense-ember cursor (`BlendMode.Plus`) and dissolving luminous arc trail.
  - **3D Hourglass**: Realistic sand physics featuring a concave upper funnel, a ~30° conical lower mound, spring inertia, and 40 independent slow-falling grains (1.8s–2.6s) that fan out in mid-air and slide down the slope.
  - **3D Candle**: Concave melted-wax pool, asymmetrical wax drips with specular edge highlights, curved wick with glowing ember tip, organic 3-sine-wave flame (using an AGSL `RuntimeShader` with FBM turbulence on Android 13+ and automatic Canvas fallback on older APIs), and rising warm micro-sparks.
- **4 Seasonal OLED Themes**: All visualizers and UI components adapt seamlessly to the 4 seasonal palettes (**Spring**, **Summer**, **Autumn**, **Winter**).

---

## How It Works

### 📱 Smartphone App (Android)
- **Triple Visual Timer Style (Zen Wheel, Hourglass & Candle)**: Switch in settings between the **Zen Wheel** (with a soft glowing cursor and trail), the **Hourglass** (with funnel/cone sand physics and slow-scattering falling grains), and the **Candle** (with melted wax pool, curved wick, and organic flame), all capped at ~40 fps with zero per-frame allocations inside `DrawScope`. Tap the active timer to hide or show the digits.
- **4 Seasonal OLED Themes**: Choose in settings between **Spring**, **Summer**, **Autumn**, and **Winter**, crafted on pure OLED dark backgrounds to minimize eye strain and battery usage.
- **Live Duration Controls**: Select **∞ (Unlimited)** for an open-ended session or adjust the timer using `-5m`, `-1m`, `+1m`, and `+5m` buttons both before starting and **live while meditating** (or paused), with the initial planned time displayed underneath the main digits.
- **Preparation Delay**: Set a custom preparation countdown in seconds before the opening gong sounds.
- **4 Real-Time Procedural Bell Timbres & Alert Modes**: Chimes are mathematically synthesized in 16-bit PCM (44.1 kHz) via `AudioTrack` with anti-click envelopes across 4 selectable timbres (**Gong** at 216 Hz, **Crystal** at 432 Hz, **Keisu** at 528 Hz, **Tingsha** at 2640 Hz), with volume control (20%–100%) and selectable alert mode (*Both*, *Bell*, *Vibrate*).
- **Intermediate Bell with Dedicated Timbre**: When an interval in minutes (> 0) is active, a dedicated selector appears right below it to pick the specific intermediate bell timbre (defaulting to **Tingsha**) independently of the main start/end bell.
- **Automatic Do Not Disturb (DND)**: When enabled and authorized in system settings, Still automatically activates Do Not Disturb when you press *Start* and restores your previous interruption filter when you *Pause* or *Save*.
- **Overtime (Extra Minutes ✨)**: When the planned time finishes, 3 closing chimes of the main bell sound. Instead of abruptly stopping, the timer counts extra minutes (Overtime), letting you save either the planned duration or include the extra time.
- **Keep Screen On & Foreground Service with Dynamic WakeLock**: Keeps the display awake during active meditation (`FLAG_KEEP_SCREEN_ON`) while pairing a `mediaPlayback` Foreground Service (`StillTimerService`) with a dynamic `PowerManager.PARTIAL_WAKE_LOCK` and `SystemClock.elapsedRealtime()` so timing and chimes remain accurate even if you manually lock the screen.
- **Local History & Smart Health Sync**: Every completed session is saved to the **Local History (`≡`)** with stats and streak tracking, and synced to Google Health Connect with deterministic deduplication (`clientRecordId`). If you connect Health Connect later on, you can push your entire local history at any time via the **"Resync history"** button in settings.
- **Google Play In-App Updates**: Silently checks for available Play Store updates on launch (only when idle) using flexible background downloads.

### ⌚ Smartwatch App (Wear OS)
- **Circular Display UI**: Page-based layout (`VerticalPager` & `HorizontalPager`) with circular progress ring, `[-]` / `[+]` duration buttons, and **rotary crown / bezel** support.
- **Haptic & Audio Chimes**: Synchronized wrist vibration (`triggerHapticGong`) and speaker chime (1 chime at start, 3 chimes at completion, plus automatic wake-up of the save screen).
- **On-Wrist Settings with 2×2 Bell Selector**: Swipe down to configure **Alert mode** (*Both*, *Bell*, *Vibrate*), **Bell timbre** via a round-screen-optimized 2×2 grid (**Crystal** 432 Hz, **Keisu** 528 Hz, **Gong** 216 Hz, **Tingsha** 2640 Hz with tap preview), **Bell volume** (20%–100%), and **Seasonal theme** (*Spring*, *Summer*, *Autumn*, *Winter*).
- **Ambient Mode & Battery Efficiency**: Supports `AmbientModeSupport` (pure black OLED background in Always-On mode) and a dynamic `PARTIAL_WAKE_LOCK` inside `StillTimerService`.
- **Quick Launch Tile**: Includes a Wear OS Tile (`StillTileService`) to open the timer with a single tap.

---

## 🔒 Google Health Connect Integration & Privacy
- **Native Mindfulness Records**: Completed sessions are written as native `MindfulnessSessionRecord` entries (`MINDFULNESS_SESSION_TYPE_MEDITATION`, title *"Still"*, `Metadata.activelyRecorded`).
- **Write-Only Permission**: Still requests **only** `WRITE_MINDFULNESS` (`androidx.health.permission.Mindfulness.WRITE` on Android 13 and `android.permission.health.WRITE_MINDFULNESS` on Android 14+). It **never reads** any health or personal data.
- **No Servers, No Accounts, Zero Ads**: 100% offline, no analytics, no trackers.
- Full policy available in [PRIVACY.md](PRIVACY.md).

---

## 🛠️ Technical Architecture & Requirements
- **Version**: `2.0.3`
- **UI & Procedural Graphics**: Jetpack Compose Material 3 and native `Canvas` on both Phone and Watch (with optional AGSL `RuntimeShader` on Android 13+ for the candle flame and ~40 fps `withFrameNanos` loops that halt completely when paused or stopped).
- **16-bit PCM Sound Synthesis**: Real-time 44.1 kHz procedural audio generation with raised-cosine anti-click envelopes via `AudioTrack` for all 4 timbres (**Gong**, **Crystal**, **Keisu**, **Tingsha**) with zero external audio assets.
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

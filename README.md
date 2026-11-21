# Still - Minimal Zen Timer (v3.0.0)

[🇮🇹 Versione Italiana](#-versione-italiana) | [🇬🇧 English Version](#-english-version)

---

# 🇮🇹 Versione Italiana

## Cos'è Still?
Still è un'applicazione open source, **100% offline** (priva di permesso `INTERNET`) per Android e Wear OS, concepita per la meditazione (Mindfulness e Vipassana). A differenza di molti altri timer sul mercato, si concentra sul minimalismo assoluto, sull'affidabilità del conteggio a schermo spento o attivo e sull'integrazione nativa e trasparente con **Google Salute (Health Connect)**.

---

## Novità della Versione 3.0.0
- **4 Stili Visivi (con "Essenziale" predefinito)**:
  - **Essenziale (predefinito)**: timer puro e contemplativo con sole cifre centrate e una sottile traccia circolare minimale di avanzamento; un tocco avvia o mostra/nasconde le cifre.
  - **Ruota Zen**: geometrie tangenziali discrete in controluce, cursore a brace d'incenso con bagliore radiale additivo (`BlendMode.Plus`) e scia luminosa.
  - **Clessidra 3D**: fisica della sabbia con concavità a imbuto superiore, cumulo conico inferiore (~30°), inerzia elastica (`spring`) e 40 granelli indipendenti a caduta lenta.
  - **Candela 3D**: laghetto concavo di cera fusa, colature asimmetriche, stoppino curvo con brace incandescente, fiamma organica (con `RuntimeShader` AGSL su Android 13+ e fallback Canvas) e **brace residua con filo di fumo procedurale** (~3.8 s) allo spegnimento.
- **9 Campane Sintetizzate in PCM a 16-bit (Smartphone & Wear OS)**: ai 4 timbri classici (**Gong** 216 Hz, **Quarzo** 432 Hz, **Keisu** 528 Hz, **Tingsha** 2640 Hz) si aggiungono **5 campane soft** con attacco morbido e riverbero leggero: **Carillon** (880 Hz), **Ciotola d'acqua** (392 Hz), **Ciotola morbida** (256 Hz), **Piuma** (660 Hz) e **Gong morbido** (196 Hz), disponibili sia su telefono (campana principale e intermedia) sia su orologio.
- **Single Source of Truth (`StateFlow`) & Calendario/Backup JSON**: su smartphone `StillTimerService` è la fonte unica dello stato del timer (riaggancio automatico della UI anche se Android ricrea `MainActivity`), notifica foreground con icona monocromatica dedicata di Still, protezione dal gesto Indietro (`BackHandler`) durante la pratica, **calendario mensile** nello storico con dettaglio giornaliero e **backup/ripristino locale in JSON** con deduplicazione automatica.
- **Esperienza Completa su Wear OS**: persistenza dell'ultima durata selezionata, conto alla rovescia di preparazione, **Complicazione per quadrante** (*Watch Face Complication* `StillComplicationService`), 9 timbri selezionabili da polso e **rilevazione silenziosa del battito cardiaco** (`BODY_SENSORS` opzionale nel Service) inviata allo storico locale del telefono.

---

## Come funziona l'App

### 📱 App Smartphone (Android)
- **4 Stili Visivi (Essenziale, Ruota Zen, Clessidra & Candela)**: scegli nelle impostazioni tra il nuovo stile **Essenziale** (predefinito), la **Ruota Zen** con cursore luminoso diffuso, la **Clessidra** animata con fisica della sabbia e la **Candela** viva con fiamma organica e filo di fumo allo spegnimento, tutti ottimizzati a ~40 fps e a zero allocazioni per frame nel `DrawScope`. Tocca il timer in corso per nascondere o mostrare le cifre.
- **4 Temi Stagionali OLED**: scegli nelle impostazioni tra **Primavera**, **Estate**, **Autunno** e **Inverno**, calibrati su sfondo nero OLED per ridurre l'affaticamento visivo e il consumo energetico.
- **Regolazione del Tempo (Anche a Sessione Avviata)**: imposta la durata o seleziona **∞ (Infinito)** per una sessione senza limiti. Puoi usare i pulsanti `-5m`, `-1m`, `+1m` e `+5m` sia prima di partire sia **in tempo reale durante la meditazione** (o in pausa) per allungare o accorciare la sessione senza interromperla, con indicazione del tempo iniziale sotto il timer.
- **Ritardo Iniziale (Preparazione)**: imposta un tempo di preparazione in secondi dalle impostazioni: Still gestisce il conto alla rovescia (*"Preparati"*) direttamente nel Foreground Service prima di far suonare il gong di inizio sessione.
- **9 Campane Sintetizzate in Tempo Reale & Modalità Avviso**: il suono viene sintetizzato matematicamente in tempo reale in PCM a 16-bit (44.1 kHz stereo con spazializzazione e riverbero comb) tramite `AudioTrack` con inviluppo anti-click su 9 timbri selezionabili (**Gong** 216 Hz, **Quarzo** 432 Hz, **Keisu** 528 Hz, **Tingsha** 2640 Hz, **Carillon** 880 Hz, **Ciotola d'acqua** 392 Hz, **Ciotola morbida** 256 Hz, **Piuma** 660 Hz, **Gong morbido** 196 Hz), con regolazione del volume (20%–100%) e scelta della modalità di avviso (*Entrambi*, *Campane*, *Vibrazione*).
- **Campana Intermedia con Timbro Dedicato**: se attivata dalle impostazioni (es. ogni 5, 10 o 15 minuti), puoi scegliere tra tutti i 9 timbri il richiamo intermedio dedicato (predefinito su **Tingsha**), indipendente dal timbro di inizio/fine meditazione.
- **Non Disturbare Automatico (DND)**: se abilitato e autorizzato nelle impostazioni di sistema, Still attiva automaticamente la modalità Non Disturbare all'avvio della sessione e ripristina in modo sicuro il filtro precedente in pausa, allo stop o al rientro dopo una chiusura forzata.
- **Overtime (Minuti Extra ✨)**: al raggiungimento del tempo previsto suonano 3 rintocchi finali della campana principale. Il timer continua a conteggiare i minuti extra (Overtime), consentendoti di decidere se salvare solo il tempo pianificato oppure includere i minuti extra.
- **Schermo Attivo, Foreground Service `StateFlow` & Protezione Back**: durante la sessione lo schermo rimane attivo (`FLAG_KEEP_SCREEN_ON`) e il tasto/gesto Indietro è bloccato (`BackHandler`) per evitare interruzioni accidentali. Il *Foreground Service* (`StillTimerService`, tipo `mediaPlayback`) con `PARTIAL_WAKE_LOCK` dinamico, icona monocromatica dedicata e stato esposto via `StateFlow` garantisce conteggio rigoroso su `SystemClock.elapsedRealtime()` e riaggancio automatico della UI anche se l'Activity viene ricreata dal sistema.
- **Calendario Mensile, Storico Locale & Backup JSON**: ogni sessione viene registrata nello **Storico Locale (`≡`)** con statistiche, serie di giorni consecutivi (attuale e record), **calendario mensile interattivo** con dettaglio giornaliero (inclusi gli eventuali BPM rilevati dall'orologio) e funzioni di **Esporta / Importa in JSON** tramite selettore di sistema con deduplicazione automatica.
- **Coda di Sincronizzazione & Risincronizzazione Health Connect**: se Google Salute è collegato, ogni sessione viene sincronizzata senza duplicati (`clientRecordId`); se temporaneamente non disponibile, viene accodata e inviata automaticamente al rientro. Con **"Risincronizza storico"** puoi inviare tutto lo storico pregresso in un solo tocco.
- **Aggiornamenti In-App (Play Store)**: all'apertura (solo quando nessuna meditazione è in corso), l'app verifica in modo silenzioso la presenza di aggiornamenti su Google Play proponendo il download flessibile in background.

### ⌚ App Orologio (Wear OS)
- **Interfaccia Ottimizzata per Schermi Circolari**: navigazione fluida a pagine (`VerticalPager` e `HorizontalPager`) con anello di avanzamento, pulsanti `[-]` / `[+]`, supporto alla **corona rotante / ghiera**, protezione dal gesto Indietro durante la sessione e **persistenza dell'ultima durata selezionata** (inclusa modalità `∞`).
- **9 Campane Sintetizzate & Feedback Aptico**: sintesi PCM a 16-bit ottimizzata per l'altoparlante mono dell'orologio per tutti e 9 i timbri (4 classici + 5 soft) con anteprima al tocco e rintocco aptico sincronizzato (`triggerHapticGong`: 1 rintocco all'inizio, 3 rintocchi all'overtime).
- **Impostazioni Complete da Polso**: scorrendo accedi a **Modalità avviso** (*Entrambi*, *Campane*, *Vibraz.*), **Volume campana** (20%–100%), selettore completo dei **9 timbri**, **Tema stagionale** e **Ritardo di preparazione**.
- **Rilevazione Silenziosa del Battito Cardiaco (Opzionale)**: se autorizzato (`BODY_SENSORS`), `StillTimerService` esegue un campionamento leggero e intermittente del sensore cardiaco durante la meditazione attiva, senza mai mostrare numeri sull'orologio. Alla chiusura della sessione, i valori sintetici (iniziale, finale, medio) vengono inviati allo storico locale del telefono tramite `Wearable Data Layer` (mai a Health Connect).
- **Modalità Ambient & Risparmio Energetico**:
  - L'Activity implementa `AmbientModeSupport` (Always-on a 1 Hz su sfondo nero puro `Color.Black` per preservare la batteria e prevenire il burn-in OLED).
  - `StillTimerService` utilizza `PARTIAL_WAKE_LOCK` dinamico, notifica con icona dedicata di Still e *Ongoing Activity* sul quadrante per rientrare subito nella sessione.
- **Scheda Rapida (Tile) & Complicazione Quadrante**: include sia una **Scheda rapida Wear OS** (`StillTileService`) sia una **Complicazione per il quadrante** (`StillComplicationService`, tipi `SHORT_TEXT` e `MONOCHROMATIC_IMAGE`) per avviare Still con un tocco.

---

## 🔒 Integrazione con Google Salute (Health Connect) & Privacy

Still adotta un approccio rigoroso e trasparente alla tutela dei dati personali:

- **100% Offline, Zero Permesso `INTERNET`**: nessuno dei due moduli dichiara il permesso `INTERNET`; nessun server, nessun account, zero pubblicità e zero tracker.
- **Mindfulness Nativa (Non come Esercizio)**: le sessioni vengono salvate come record nativi `MindfulnessSessionRecord` (`MINDFULNESS_SESSION_TYPE_MEDITATION`, titolo *"Still"*, `Metadata.activelyRecorded`) nella sezione **Mindfulness / Benessere mentale** di Google Salute.
- **Permessi Minimi di Sola Scrittura su Health Connect**: Still richiede unicamente `WRITE_MINDFULNESS` (`androidx.health.permission.Mindfulness.WRITE` su Android 13 e `android.permission.health.WRITE_MINDFULNESS` su Android 14+) e **non legge alcun dato** da Health Connect.
- **Battito Cardiaco Opzionale Solo Locale su Wear OS**: se l'utente concede `BODY_SENSORS` sull'orologio, i BPM sono letti solo localmente durante la sessione e salvati nello storico locale del telefono, senza invio a Health Connect o a terze parti.
- **Informativa sui Permessi di Sistema**: supporta gli intent nativi `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13) e `android.intent.action.VIEW_PERMISSION_USAGE` protetto da `START_VIEW_PERMISSION_USAGE` (Android 14+).
- L'informativa completa è consultabile nel file [PRIVACY.md](PRIVACY.md).

---

## 🛠️ Scelte Tecniche e Architettura
- **Versione**: `3.0.0`
- **Architettura 100% Offline & Compatibilità Play Store**: nessun permesso `INTERNET` in `:app` e `:wear`; `android.hardware.screen.portrait` dichiarato con `required="false"` su `:app` per piena compatibilità su tablet, pieghevoli, Chromebook e dispositivi XR.
- **UI Dichiarativa & Grafica Procedurale**: realizzata interamente con **Jetpack Compose Material 3** e `Canvas` nativo (con `RuntimeShader` AGSL opzionale su Android 13+ per la fiamma della candela e loop `withFrameNanos` limitati a ~40 fps che si arrestano automaticamente in pausa/stop).
- **Health Connect Client**: utilizza le API ufficiali `androidx.health.connect.client.HealthConnectClient`.
- **Servizi in Background e Affidabilità**:
  - Smartphone: `ForegroundService` (`mediaPlayback`) come *Single Source of Truth* via `StateFlow` + `PARTIAL_WAKE_LOCK` dinamico calibrato sulla durata.
  - Orologio: `ForegroundService` (`specialUse`, proprietà `Meditation timer and background chime`) con campionamento HR resiliente al lifecycle UI, *Ongoing Activity* e risveglio della schermata di salvataggio a fine sessione.
- **Sintesi Sonora PCM a 16-bit**: generazione audio procedurale a 44.1 kHz (stereo su smartphone, mono adattato su Wear OS) con inviluppo anti-click (*raised-cosine*) riprodotta tramite `AudioTrack` per tutti e 9 i timbri senza dipendenze da file audio esterni.
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
**Still** is a **100% offline** (zero `INTERNET` permission), open-source meditation timer for Android smartphones and Wear OS smartwatches (Mindfulness and Vipassana). Unlike bloated commercial apps, Still focuses on absolute minimalism, rock-solid background timing, and native, privacy-first integration with **Google Health Connect**.

---

## What's New in Version 3.0.0
- **4 Visual Styles (with "Essential" as the new default)**:
  - **Essential (default)**: ultra-clean contemplative timer with centered digits and a whisper-thin circular progress track; tap to start or toggle digit visibility.
  - **Zen Wheel**: subtle backlit internal geometry, diffused incense-ember cursor (`BlendMode.Plus`), and luminous trail.
  - **3D Hourglass**: realistic sand physics with a concave upper funnel, ~30° conical lower mound, spring inertia, and 40 independent slow-falling grains.
  - **3D Candle**: concave melted-wax pool, asymmetrical drips, curved glowing wick, organic flame (AGSL `RuntimeShader` on Android 13+ with Canvas fallback), and a **dying ember with procedural rising smoke wisp** (~3.8 s) upon extinguishing.
- **9 Synthesized 16-bit PCM Bell Timbres (Phone & Wear OS)**: alongside the 4 classic timbres (**Gong** 216 Hz, **Crystal** 432 Hz, **Keisu** 528 Hz, **Tingsha** 2640 Hz), 5 new **soft bells** with gentle envelopes and subtle reverb are available across both phone and watch: **Chime** (880 Hz), **Water Bowl** (392 Hz), **Soft Bowl** (256 Hz), **Feather** (660 Hz), and **Soft Gong** (196 Hz).
- **Single Source of Truth (`StateFlow`) & Calendar / JSON Backup**: on smartphones, `StillTimerService` owns active timer state via `StateFlow` for automatic UI reattachment if Android recreates `MainActivity`, paired with a dedicated monochrome Still notification icon, `BackHandler` protection while active, a **monthly calendar history view** with daily drill-down, and **local JSON export/import backup** with automatic deduplication.
- **Complete Wear OS Experience**: persisted last selected duration, preparation countdown, **Watch Face Complication** (`StillComplicationService`), all 9 bell timbres on wrist, and **silent heart-rate sampling** (optional `BODY_SENSORS` handled inside `StillTimerService`) synced to the phone's local history.

---

## How It Works

### 📱 Smartphone App (Android)
- **4 Visual Timer Styles (Essential, Zen Wheel, Hourglass & Candle)**: choose in settings between the new **Essential** default style, **Zen Wheel** (glowing cursor and trail), **Hourglass** (funnel/cone sand physics), and **Candle** (melted wax, organic flame, and extinguishing smoke wisp), all capped at ~40 fps with zero per-frame allocations inside `DrawScope`. Tap the active timer to hide or show digits.
- **4 Seasonal OLED Themes**: choose between **Spring**, **Summer**, **Autumn**, and **Winter**, calibrated on pure OLED dark backgrounds to minimize eye strain and battery usage.
- **Live Duration Controls**: select **∞ (Unlimited)** for an open-ended session or adjust the timer using `-5m`, `-1m`, `+1m`, and `+5m` buttons both before starting and **live while meditating** (or paused), with the initial planned time displayed underneath the main digits.
- **Preparation Delay**: configure a custom preparation countdown in seconds handled directly inside the Foreground Service before the opening chime sounds.
- **9 Real-Time Procedural Bell Timbres & Alert Modes**: chimes are mathematically synthesized in real time in 16-bit PCM (44.1 kHz stereo with spatialization and comb reverb) via `AudioTrack` with anti-click envelopes across 9 selectable timbres (**Gong** 216 Hz, **Crystal** 432 Hz, **Keisu** 528 Hz, **Tingsha** 2640 Hz, **Chime** 880 Hz, **Water Bowl** 392 Hz, **Soft Bowl** 256 Hz, **Feather** 660 Hz, **Soft Gong** 196 Hz), with volume control (20%–100%) and selectable alert mode (*Both*, *Bell*, *Vibrate*).
- **Intermediate Bell with Dedicated Timbre**: when an interval in minutes (> 0) is active, pick any of the 9 timbres specifically for intermediate interval chimes (defaulting to **Tingsha**) independently of the main start/end bell.
- **Automatic Do Not Disturb (DND)**: when enabled and authorized in system settings, Still activates Do Not Disturb on start and safely restores your previous interruption filter on pause, stop, or recovery after a forced process kill.
- **Overtime (Extra Minutes ✨)**: when planned time completes, 3 closing chimes sound. Instead of abruptly stopping, the timer counts extra minutes (Overtime), letting you save either only the planned duration or include the extra time.
- **Keep Screen On, `StateFlow` Foreground Service & Back Protection**: keeps the display awake (`FLAG_KEEP_SCREEN_ON`) and guards against accidental Back gestures (`BackHandler`) while active. `StillTimerService` (`mediaPlayback`) pairs a dynamic `PARTIAL_WAKE_LOCK`, `SystemClock.elapsedRealtime()`, a dedicated monochrome Still notification icon, and a `StateFlow` Single Source of Truth so UI reattaches seamlessly even after Activity recreation.
- **Monthly Calendar, Local History & JSON Backup**: every completed session is logged in **Local History (`≡`)** with stats, current/best streak, an **interactive monthly calendar** with daily details (including optional watch BPM metrics), and manual **JSON Export / Import** via the system picker with deterministic deduplication.
- **Smart Health Connect Sync**: syncs completed sessions with deterministic `clientRecordId` deduplication, automatically queues offline/unavailable writes, and offers one-tap **"Resync history"** in settings.
- **Google Play In-App Updates**: silently checks for Play Store updates on launch (only when idle) using flexible background downloads.

### ⌚ Smartwatch App (Wear OS)
- **Circular Display UI**: smooth page navigation (`VerticalPager` & `HorizontalPager`) with circular progress ring, `[-]` / `[+]` duration buttons, **rotary crown / bezel** support, Back protection while active, and **persisted last selected duration** (including `∞`).
- **9 Synthesized Bell Timbres & Haptic Chimes**: mono-tuned 16-bit PCM synthesis for all 9 timbres (4 classic + 5 soft) with tap preview and synchronized wrist vibration (`triggerHapticGong`: 1 chime at start, 3 chimes at completion).
- **Full On-Wrist Settings**: swipe to configure **Alert mode** (*Both*, *Bell*, *Vibrate*), **Bell volume** (20%–100%), all **9 bell timbres**, **Seasonal theme**, and **Preparation delay**.
- **Silent Heart Rate Sampling (Optional)**: when granted (`BODY_SENSORS`), `StillTimerService` performs intermittent background heart-rate sampling during active meditation without ever displaying numbers on the watch. Upon completion, summary BPM values (start, end, average) are sent to the phone's local history via `Wearable Data Layer` (never written to Health Connect).
- **Ambient Mode & Battery Efficiency**: supports `AmbientModeSupport` (pure black OLED background in Always-On mode), dynamic `PARTIAL_WAKE_LOCK`, dedicated Still notification icon, and *Ongoing Activity* return chip.
- **Quick Launch Tile & Watch Face Complication**: ships with both a Wear OS **Tile** (`StillTileService`) and a **Watch Face Complication** (`StillComplicationService`, supporting `SHORT_TEXT` and `MONOCHROMATIC_IMAGE`) for one-tap launch.

---

## 🔒 Google Health Connect Integration & Privacy
- **100% Offline, Zero `INTERNET` Permission**: neither module declares `INTERNET`; no servers, no accounts, zero ads, zero trackers.
- **Native Mindfulness Records**: completed sessions are written as native `MindfulnessSessionRecord` entries (`MINDFULNESS_SESSION_TYPE_MEDITATION`, title *"Still"*, `Metadata.activelyRecorded`).
- **Write-Only Health Connect Permission**: Still requests **only** `WRITE_MINDFULNESS` (`androidx.health.permission.Mindfulness.WRITE` on Android 13 and `android.permission.health.WRITE_MINDFULNESS` on Android 14+) and **never reads** any Health Connect data.
- **Optional Local-Only Watch Heart Rate**: when `BODY_SENSORS` is granted on Wear OS, heart-rate readings stay strictly local in the phone's session history and are never sent to Health Connect or external servers.
- Full policy available in [PRIVACY.md](PRIVACY.md).

---

## 🛠️ Technical Architecture & Requirements
- **Version**: `3.0.0`
- **100% Offline & Play Store Compatibility**: no `INTERNET` permission in `:app` or `:wear`; `android.hardware.screen.portrait` declared with `required="false"` in `:app` for broad compatibility across tablets, foldables, Chromebooks, and XR devices.
- **UI & Procedural Graphics**: Jetpack Compose Material 3 and native `Canvas` on both Phone and Watch (with optional AGSL `RuntimeShader` on Android 13+ for the candle flame and ~40 fps `withFrameNanos` loops that halt completely when paused or stopped).
- **Reliable Background Services**:
  - Phone: `ForegroundService` (`mediaPlayback`) exposing a `StateFlow` Single Source of Truth + dynamic `PARTIAL_WAKE_LOCK`.
  - Watch: `ForegroundService` (`specialUse`, `Meditation timer and background chime`) with UI-resilient HR sampling, *Ongoing Activity*, and completion wake-up.
- **16-bit PCM Sound Synthesis**: real-time 44.1 kHz procedural audio generation (stereo on phone, mono-tuned on Wear OS) with raised-cosine anti-click envelopes via `AudioTrack` for all 9 timbres with zero external audio assets.
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

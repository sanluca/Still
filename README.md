# Still - Vipassana Meditation Timer

## Cos'è Still?
Still è un'applicazione open source per Android e Wear OS, concepita per la meditazione Vipassana. A differenza di molti altri timer sul mercato, si concentra sul minimalismo assoluto, sull'affidabilità a schermo spento e sull'integrazione diretta e precisa con **Google Salute (Health Connect)**.

## Come funziona l'App

### 📱 App Smartphone (Android)
- **Quadrante Principale**: Scegli il tempo dai preset in basso (es. 5m, 10m) oppure seleziona **∞ (Infinito)** per una sessione libera senza limite.
- **Pulsanti + / -**: Aggiungi o togli tempo al timer pre-esistente con precisione.
- **Inizia**: L'app attiva immediatamente un *Foreground Service* invisibile (StillTimerService) e suona un gong. Da questo momento, puoi tranquillamente **spegnere lo schermo** o usare altre app: il timer continuerà a girare grazie a un PowerManager WakeLock sicuro.
- **La Campana Intermedia**: Se la attivi (es. ogni 10 min), un suono gentile del gong suonerà in background per richiamare la tua attenzione durante lunghe sessioni, senza mai interrompere il timer.
- **Non Disturbare (DND)**: Se attivato dalle impostazioni ⚙️ e se sono stati concessi i permessi di sistema, Still azzitterà il telefono non appena premi 'Inizia', e lo riporterà alla normalità quando premi 'Pausa' o 'Salva'.
- **Overtime (Extra ✨)**: Se raggiungi il tempo previsto, suoneranno 3 rintocchi finali. Tuttavia, il timer NON si ferma da solo, ma inizia a conteggiare i minuti extra (Overtime). Potrai decidere alla fine se includerli o scartarli.
- **Salvataggio**: Al termine, premi 'Salva'. Still dedurrà matematicamente l'ora esatta di inizio a ritroso e la invierà a **Google Connessione Salute** marcandola ufficialmente come 'Meditazione'. Inoltre, la salverà nel tuo **Storico Locale (📊)**.

### ⌚ App Orologio (Wear OS)
- L'app è strutturata in un unico listato a scorrimento (rotellina o touch).
- **In alto (Quadrante)**: Vedi il timer. Funziona esattamente come il telefono.
- **In basso (Impostazioni)**: Scorrendo verso il basso, puoi regolare i minuti, cambiare la frequenza (Hz) del gong, e regolare i secondi di preparazione (Ritardo iniziale).
- **Sincronizzazione**: L'orologio comunica con il telefono (se connesso via Bluetooth). Quando salvi una meditazione sull'orologio, questa viene **spedita in background al telefono**, che a sua volta la caricherà su Google Salute.
- **Backup Offline**: Se ti alleni solo con l'orologio al polso (senza telefono), le tue meditazioni verranno salvate in una coda interna. Non appena il telefono tornerà nel raggio Bluetooth e aprirai l'app, l'orologio vuoterà la coda aggiornando lo storico del telefono in blocco!

## Scelte Tecniche (Architettura)
- **Health Connect**: Per garantire la lettura anche da vecchi dispositivi e app legacy, la sessione viene inviata come ExerciseSessionRecord indicando l'ID **82** (EXERCISE_TYPE_MEDITATION). Questo previene il 'bug' per cui alcune app di terze parti riportano '0 minuti' quando si usa la API nativa sperimentale Mindfulness.
- **Foreground Service**: Entrambe le app usano WakeLock. L'app del telefono vanta un Service di Background dedicato (mediaPlayback).
- **Audio**: Suoni sintetizzati in tempo reale tramite formula matematica (senza file mp3 statici) generati e inviati all'AudioTrack a seconda degli Hz (432Hz, 528Hz, 216Hz) scelti.

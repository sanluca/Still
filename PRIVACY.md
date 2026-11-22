# Privacy Policy - Still (Informativa sulla Privacy — v3.0.0)

*Ultimo aggiornamento / Last updated: 22 Novembre 2026*

---

## 🇮🇹 Versione Italiana

Questa informativa sulla privacy descrive come l'applicazione **Still** (sia per smartphone Android che per smartwatch Wear OS) gestisce i dati dell'utente, con particolare riferimento ai dati relativi al benessere, all'integrazione con **Google Salute (Health Connect)**, al sensore opzionale del battito cardiaco su Wear OS e ai file audio locali opzionali.

### 1. Filosofia e Trasparenza
**Still** è un'applicazione open source per la meditazione progettata nel pieno rispetto della privacy dell'utente:
- **100% Offline (nessun permesso `INTERNET`)**: né il modulo smartphone né il modulo Wear OS dichiarano il permesso di accesso a Internet.
- **Nessun account esterno richiesto**: non è richiesta alcuna registrazione o creazione di profilo.
- **Nessun server proprietario**: Still non possiede server proprietari e non trasmette alcun dato a server di terze parti o servizi analytics.
- **Nessuna pubblicità o tracker**: l'app non contiene annunci pubblicitari, librerie di profilazione o strumenti di tracciamento o analytics di terze parti.

### 2. Dati di Google Salute (Health Connect) e Sensore Battito Cardiaco (Wear OS)

#### Google Salute (Health Connect — Smartphone)
Still si integra con Google Salute (Health Connect) per permettere agli utenti di registrare le proprie sessioni di meditazione.
- **Permessi richiesti**: Still richiede esclusivamente il permesso di **scrittura** per le sessioni di meditazione/mindfulness (`WRITE_MINDFULNESS` / `MindfulnessSessionRecord`).
- **Nessuna lettura da Health Connect**: Still **non legge**, non accede e non monitora alcun dato personale o sanitario presente nel tuo account Google Salute (come frequenza cardiaca, sonno, calorie, passi, ecc.).
- **Scopo dell'utilizzo**: L'accesso in scrittura viene utilizzato unicamente per salvare data, orario di inizio, orario di fine e durata della sessione di meditazione completata, al solo fine di mantenere aggiornato il registro del benessere dell'utente.
- **Condivisione**: I dati inviati a Health Connect rimangono gestiti direttamente da Google e dalle policy di sicurezza del sistema operativo Android. Still non vende, non condivide e non trasferisce tali dati a terzi.

#### Sensore Battito Cardiaco Opzionale (`READ_HEART_RATE` / `BODY_SENSORS` — Wear OS)
L'app per smartwatch Wear OS dichiara i permessi opzionali `android.permission.health.READ_HEART_RATE` (Android 16 / API 36 e superiori) e `android.permission.BODY_SENSORS` (versioni precedenti):
- **Raccolta opzionale e locale**: solo se espressamente autorizzato dall'utente, l'orologio legge in modo intermittente e locale il sensore del battito cardiaco (`Sensor.TYPE_HEART_RATE`) esclusivamente durante una sessione di meditazione attiva (dopo l'eventuale preparazione) per calcolare i valori sintetici della sessione (iniziale, finale e medio in BPM).
- **Nessuna visualizzazione invasiva e nessuna condivisione**: i valori BPM non vengono mostrati sullo schermo dell'orologio durante la pratica, **non vengono inviati a Google Salute (Health Connect)** e **non vengono mai trasmessi a server esterni**. Vengono conservati unicamente nello storico locale della sessione sullo smartphone abbinato.
- **Utilizzo facoltativo**: se l'utente rifiuta o revoca il permesso, il timer e tutte le funzioni di meditazione su Wear OS continuano a funzionare regolarmente senza alcuna limitazione.

### 3. Dati Memorizzati sul Dispositivo, File Locali e Backup
- **Preferenze, Impostazioni, Preset e Promemoria**: le preferenze dell'app su smartphone e smartwatch (es. durata del timer, stile visivo, timbro e volume delle campane, sottofondo sonoro e relativo volume, intervallo campane, attivazione automatica Non Disturbare su smartphone, tema colori), gli eventuali **preset di meditazione** salvati su smartphone (nome e parametri configurati) e la configurazione opzionale dei **promemoria di meditazione** (stato attivo/disattivo, orario, frequenza e giorni scelti) vengono salvati esclusivamente in locale nella memoria privata del dispositivo tramite `SharedPreferences`.
- **Promemoria di Meditazione Locali (Opzionale — Smartphone)**: disabilitati di default. Se abilitati dall'utente, Still pianifica notifiche esclusivamente locali sul dispositivo tramite `AlarmManager` e il permesso di sistema `RECEIVE_BOOT_COMPLETED` (usato solo per riprogrammare il promemoria locale dopo un riavvio del dispositivo o cambio di orario/fuso orario) e `POST_NOTIFICATIONS` per mostrare la notifica silenziosa. Prima di inviare il promemoria, Still verifica localmente sullo storico del dispositivo se l'utente ha già meditato oggi. Non vengono usati server push, Firebase Cloud Messaging o servizi di rete esterni.
- **File Audio Personali (Opzionale — Smartphone)**: se l'utente sceglie una propria traccia di sottofondo (`.mp3` o `.ogg`) nelle impostazioni globali o in un preset tramite il selettore file di sistema di Android (*Storage Access Framework* / `OpenDocument`), Still salva localmente solo l'URI persistente e il nome del file per riprodurlo in locale sul dispositivo. Still non accede ad altri file del telefono e non carica né condivide mai i file audio selezionati.
- **Storico Locale**: l'elenco delle sessioni completate (inclusi gli eventuali dati BPM rilevati dall'orologio) viene conservato esclusivamente in locale sul dispositivo. L'utente può eliminare in qualsiasi momento le singole sessioni dallo storico interno dell'app.
- **Esportazione e Importazione Manuale (Backup CSV)**: dallo storico dello smartphone l'utente può esportare o reimportare manualmente l'intero storico delle sessioni in un file **CSV UTF-8** utilizzando il selettore file di sistema di Android (*Storage Access Framework*). Il file esportato contiene le sessioni registrate (inclusi gli eventuali valori BPM associati) e viene salvato esclusivamente nella posizione scelta dall'utente; in fase di importazione i duplicati vengono filtrati automaticamente.
- **Backup di Sistema Android**:
  - Su **smartphone (`:app`)**, se l'utente ha abilitato il backup del dispositivo nelle impostazioni di Android, solo `still_prefs.xml` (impostazioni, preset, promemoria e storico locale) viene incluso nel servizio di backup cifrato di Google (Google Drive o trasferimento verso un nuovo dispositivo), mentre lo stato temporaneo Non Disturbare (`still_dnd_state.xml`) rimane escluso.
  - Su **smartwatch (`:wear`)**, il backup di sistema è disabilitato (`allowBackup="false"`).

### 4. Comunicazione tra Smartphone e Smartwatch (Wear OS)
La comunicazione tra l'app per smartwatch (Wear OS) e l'app per smartphone avviene esclusivamente in modo locale e sicuro tramite i servizi ufficiali di Google (`Google Play Services Wearable Data Layer`), via Bluetooth o rete locale sicura. Il canale viene utilizzato **unicamente per trasferire le sessioni di meditazione completate dall'orologio allo smartphone** (durata, timestamp di inizio/fine ed eventuali valori BPM opzionali rilevati durante la sessione) ai fini dell'archiviazione nello storico locale e dell'invio della sola sessione mindfulness a Health Connect. Non viene effettuata alcuna sincronizzazione automatica delle impostazioni tra i due dispositivi.

### 5. Controllo dell'Utente e Cancellazione dei Dati
- L'utente ha il pieno controllo sui permessi di Health Connect, che possono essere revocati in qualsiasi momento tramite le *Impostazioni di Sistema Android > Sicurezza e Privacy > Connessione Salute*.
- I promemoria di meditazione su smartphone possono essere disattivati in qualsiasi momento dalle impostazioni di Still oppure silenziati/gestiti tramite le *Impostazioni di Sistema Android > App > Still > Notifiche*.
- Il permesso per il battito cardiaco su Wear OS (`READ_HEART_RATE` su Android 16+ / `BODY_SENSORS` su versioni precedenti) può essere concesso o revocato in qualsiasi momento dalle *Impostazioni dell'orologio > App > Autorizzazioni*.
- Le sessioni registrate su Health Connect possono essere visualizzate o eliminate direttamente dall'app di sistema Health Connect / Google Salute.
- La disinstallazione dell'applicazione Still comporta la cancellazione automatica di tutti i dati memorizzati localmente sul dispositivo (a meno che non vengano successivamente ripristinati tramite il backup di sistema Google Android o tramite un file CSV esportato manualmente dall'utente).

### 6. Contatti
Per domande relative alla privacy o al codice sorgente dell'applicazione, è possibile aprire una segnalazione (issue) direttamente sul repository GitHub del progetto:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

---

## 🇬🇧 English Version

This Privacy Policy describes how the **Still** application (for Android smartphones and Wear OS smartwatches) handles user data, particularly concerning wellness data, integration with **Google Health Connect**, the optional Wear OS heart rate sensor, and optional local audio files.

### 1. Core Principles
**Still** is an open-source meditation timer built with privacy as a foundational principle:
- **100% Offline (No `INTERNET` Permission)**: Neither the smartphone module nor the Wear OS module declares the Internet access permission.
- **No Account Required**: No account creation, login, or personal profile is needed.
- **No Proprietary Servers**: Still does not operate remote servers and never transmits user data to any external backend or third-party analytics service.
- **Zero Ads & Trackers**: The application contains no advertisements, analytics SDKs, tracking libraries, or profiling services.

### 2. Google Health Connect Data & Optional Heart Rate Sensor (Wear OS)

#### Google Health Connect (Smartphone)
Still integrates with Google Health Connect solely to allow users to sync their meditation sessions.
- **Permissions Requested**: Still exclusively requests **write-only** permission for mindfulness/meditation sessions (`WRITE_MINDFULNESS` / `MindfulnessSessionRecord`).
- **No Read Access to Health Connect**: Still **never reads**, accesses, or inspects any personal or health records stored in Google Health Connect (such as heart rate, sleep data, calories, step counts, etc.).
- **Data Usage Purpose**: Write permission is used exclusively to record the start time, end time, and duration of completed meditation sessions in Health Connect.
- **Data Sharing**: Data written to Health Connect is managed by Google according to Android's privacy framework. Still does not sell, share, or disclose health data to any third party.

#### Optional Heart Rate Sensor (`READ_HEART_RATE` / `BODY_SENSORS` — Wear OS)
The Wear OS smartwatch app declares the optional `android.permission.health.READ_HEART_RATE` (Android 16 / API 36+) and `android.permission.BODY_SENSORS` (earlier versions) permissions:
- **Optional Local-Only Sampling**: Only when explicitly granted by the user, the watch performs lightweight, intermittent local readings from the heart rate sensor (`Sensor.TYPE_HEART_RATE`) strictly during an active meditation session (after preparation) to compute session summary metrics (start, end, and average BPM).
- **Silent & Never Shared Externally**: BPM figures are never displayed on the watch screen during meditation, **are never written to Google Health Connect**, and **are never sent to external servers**. They are saved strictly within the local session history on the paired smartphone.
- **Fully Optional**: If the user denies or revokes this permission, the timer and all meditation features on Wear OS continue working normally without restriction.

### 3. Local Data Storage, User Files & Backups
- **Settings, Preferences, Presets & Reminders**: User configurations on phone and watch (such as timer duration, visual style, bell sound and volume, ambient soundscape and volume, interval chime settings, Auto Do Not Disturb on smartphone, and color theme), any **meditation presets** saved on smartphone (preset name and configured parameters), and optional **meditation reminder** preferences (enabled state, time, frequency, and selected days) are stored strictly locally in Android's private `SharedPreferences`.
- **Local Meditation Reminders (Optional — Smartphone)**: Disabled by default. When enabled by the user, Still schedules strictly on-device local notifications via Android `AlarmManager`, using `RECEIVE_BOOT_COMPLETED` solely to restore the local schedule after a reboot or time/timezone change and `POST_NOTIFICATIONS` to display the silent reminder. Before posting a reminder, Still checks only the on-device session history to skip the alert if the user has already meditated today. No push servers, Firebase Cloud Messaging, or external network services are used.
- **Custom Local Audio Files (Optional — Smartphone)**: If the user selects a personal background track (`.mp3` or `.ogg`) in global settings or in a preset via Android's system file picker (*Storage Access Framework* / `OpenDocument`), Still stores only the persistable file URI and display name locally to play the file on-device. Still cannot access any other files on the device and never uploads or shares selected audio files.
- **Session History**: Meditation logs (including any optional BPM values recorded from the watch) are saved solely on the local device. Users can manually delete any session record from the history screen at any time.
- **Manual CSV Backup (Export / Import)**: From the smartphone history screen, users can manually export or import their complete session history in **UTF-8 CSV** format via Android's system file picker (*Storage Access Framework*). Exported CSV files include any BPM data saved in local history and are written only to the location chosen by the user; duplicate sessions are automatically skipped on import.
- **Android System Backup**:
  - On **smartphones (`:app`)**, if the user has enabled device backup in Android settings, only `still_prefs.xml` (preferences, presets, reminders, and local session history) is included in Google's encrypted system backup service (Google Drive or device-to-device transfer), while temporary Do Not Disturb state (`still_dnd_state.xml`) is excluded.
  - On **smartwatches (`:wear`)**, system backup is disabled (`allowBackup="false"`).

### 4. Watch and Phone Communication (Wear OS)
Communication between the Wear OS watch and paired Android phone takes place locally and securely via official Google Play Services (`Wearable Data Layer`) over Bluetooth or local Wi-Fi. This channel is strictly limited to **transferring completed meditation sessions from the watch to the phone** (duration, start/end timestamps, and any optional BPM metrics recorded during the session) for local history logging and writing the mindfulness session to Health Connect. No automatic synchronization of app settings occurs between devices.

### 5. User Rights and Data Deletion
- Users can revoke Health Connect permissions at any time via Android *Settings > Security & Privacy > Health Connect*.
- Smartphone meditation reminders can be disabled at any time in Still settings or muted/managed via Android *Settings > Apps > Still > Notifications*.
- The Wear OS heart rate permission (`READ_HEART_RATE` on Android 16+ / `BODY_SENSORS` on earlier versions) can be granted or revoked at any time via watch *Settings > Apps > Permissions*.
- Sessions saved to Health Connect can be viewed or deleted at any time through the Android Health Connect system interface.
- Uninstalling the application deletes all locally stored preferences and history from the device (unless subsequently restored via Android's Google system backup or a user-exported CSV backup file).

### 6. Contact
For any inquiries regarding this policy or the application source code, please open an issue on the project GitHub repository:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

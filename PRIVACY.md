# Privacy Policy - Still (Informativa sulla Privacy)

*Ultimo aggiornamento / Last updated: 20 Novembre 2026*

---

## 🇮🇹 Versione Italiana

Questa informativa sulla privacy descrive come l'applicazione **Still** (sia per smartphone Android che per smartwatch Wear OS) gestisce i dati dell'utente, con particolare riferimento ai dati relativi al benessere, all'integrazione con **Google Connessione Salute (Health Connect)** e al sensore opzionale del battito cardiaco su Wear OS.

### 1. Filosofia e Trasparenza
**Still** è un'applicazione open source per la meditazione progettata nel pieno rispetto della privacy dell'utente:
- **100% Offline (nessun permesso `INTERNET`)**: né il modulo smartphone né il modulo Wear OS dichiarano il permesso di accesso a Internet.
- **Nessun account esterno richiesto**: non è richiesta alcuna registrazione o creazione di profilo.
- **Nessun server proprietario**: Still non possiede server proprietari e non trasmette alcun dato a server di terze parti o servizi analytics.
- **Nessuna pubblicità o tracker**: l'app non contiene annunci pubblicitari, librerie di profilazione o strumenti di tracciamento o analytics di terze parti.

### 2. Dati di Google Connessione Salute (Health Connect) e Sensore Battito Cardiaco (Wear OS)

#### Google Connessione Salute (Health Connect — Smartphone)
Still si integra con Google Connessione Salute per permettere agli utenti di registrare le proprie sessioni di meditazione.
- **Permessi richiesti**: Still richiede esclusivamente il permesso di **scrittura** per le sessioni di meditazione/mindfulness (`WRITE_MINDFULNESS` / `MindfulnessSessionRecord`).
- **Nessuna lettura da Health Connect**: Still **non legge**, non accede e non monitora alcun dato personale o sanitario presente nel tuo account Google Connessione Salute (come frequenza cardiaca, sonno, calorie, passi, ecc.).
- **Scopo dell'utilizzo**: L'accesso in scrittura viene utilizzato unicamente per salvare data, orario di inizio, orario di fine e durata della sessione di meditazione completata, al solo fine di mantenere aggiornato il registro del benessere dell'utente.
- **Condivisione**: I dati inviati a Health Connect rimangono gestiti direttamente da Google e dalle policy di sicurezza del sistema operativo Android. Still non vende, non condivide e non trasferisce tali dati a terzi.

#### Sensore Battito Cardiaco Opzionale (`BODY_SENSORS` — Wear OS)
L'app per smartwatch Wear OS dichiara il permesso opzionale `android.permission.BODY_SENSORS`:
- **Raccolta opzionale e locale**: solo se espressamente autorizzato dall'utente, l'orologio legge in modo intermittente e locale il sensore del battito cardiaco (`Sensor.TYPE_HEART_RATE`) esclusivamente durante una sessione di meditazione attiva (dopo l'eventuale preparazione) per calcolare i valori sintetici della sessione (iniziale, finale e medio in BPM).
- **Nessuna visualizzazione invasiva e nessuna condivisione**: i valori BPM non vengono mostrati sullo schermo dell'orologio durante la pratica, **non vengono inviati a Google Connessione Salute (Health Connect)** e **non vengono mai trasmessi a server esterni**. Vengono conservati unicamente nello storico locale della sessione sullo smartphone abbinato.
- **Utilizzo facoltativo**: se l'utente rifiuta o revoca il permesso `BODY_SENSORS`, il timer e tutte le funzioni di meditazione su Wear OS continuano a funzionare regolarmente senza alcuna limitazione.

### 3. Dati Memorizzati sul Dispositivo e Backup
- **Preferenze e Impostazioni**: le preferenze dell'app su smartphone e smartwatch (es. durata del timer, stile visivo, timbro e volume delle campane, intervallo campane, attivazione automatica Non Disturbare su smartphone, tema colori) vengono salvate localmente nella memoria privata del dispositivo tramite `SharedPreferences`.
- **Storico Locale**: l'elenco delle sessioni completate (inclusi gli eventuali dati BPM rilevati dall'orologio) viene conservato esclusivamente in locale sul dispositivo. L'utente può eliminare in qualsiasi momento le singole sessioni dallo storico interno dell'app.
- **Esportazione e Importazione Manuale (Backup JSON)**: dallo storico dello smartphone l'utente può esportare o reimportare manualmente l'intero storico delle sessioni in un file **JSON** utilizzando il selettore file di sistema di Android (Storage Access Framework). Il file esportato contiene le sessioni registrate (inclusi gli eventuali valori BPM associati) e viene salvato esclusivamente nella posizione scelta dall'utente; in fase di importazione i duplicati vengono filtrati automaticamente.
- **Backup di Sistema Android**: se l'utente ha abilitato il backup del dispositivo nelle impostazioni di Android, le impostazioni e lo storico locale possono essere inclusi nel servizio di backup cifrato di Google (Google Drive o trasferimento diretto verso un nuovo dispositivo).

### 4. Comunicazione tra Smartphone e Smartwatch (Wear OS)
La comunicazione tra l'app per smartwatch (Wear OS) e l'app per smartphone avviene esclusivamente in modo locale e sicuro tramite i servizi ufficiali di Google (`Google Play Services Wearable Data Layer`), via Bluetooth o rete locale sicura. Il canale viene utilizzato **unicamente per trasferire le sessioni di meditazione completate dall'orologio allo smartphone** (durata, timestamp di inizio/fine ed eventuali valori BPM opzionali rilevati durante la sessione) ai fini dell'archiviazione nello storico locale e dell'invio della sola sessione mindfulness a Health Connect. Non viene effettuata alcuna sincronizzazione automatica delle impostazioni tra i due dispositivi.

### 5. Controllo dell'Utente e Cancellazione dei Dati
- L'utente ha il pieno controllo sui permessi di Health Connect, che possono essere revocati in qualsiasi momento tramite le *Impostazioni di Sistema Android > Sicurezza e Privacy > Connessione Salute*.
- Il permesso `BODY_SENSORS` su Wear OS può essere concesso o revocato in qualsiasi momento dalle *Impostazioni dell'orologio > App > Autorizzazioni > Sensori*.
- Le sessioni registrate su Health Connect possono essere visualizzate o eliminate direttamente dall'app di sistema Health Connect / Google Salute.
- La disinstallazione dell'applicazione Still comporta la cancellazione automatica di tutti i dati memorizzati localmente sul dispositivo (a meno che non vengano successivamente ripristinati tramite il backup di sistema Google Android o tramite un file JSON esportato manualmente dall'utente).

### 6. Contatti
Per domande relative alla privacy o al codice sorgente dell'applicazione, è possibile aprire una segnalazione (issue) direttamente sul repository GitHub del progetto:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

---

## 🇬🇧 English Version

This Privacy Policy describes how the **Still** application (for Android smartphones and Wear OS smartwatches) handles user data, particularly concerning wellness data, integration with **Google Health Connect**, and the optional Wear OS heart rate sensor.

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

#### Optional Heart Rate Sensor (`BODY_SENSORS` — Wear OS)
The Wear OS smartwatch app declares the optional `android.permission.BODY_SENSORS` permission:
- **Optional Local-Only Sampling**: Only when explicitly granted by the user, the watch performs lightweight, intermittent local readings from the heart rate sensor (`Sensor.TYPE_HEART_RATE`) strictly during an active meditation session (after preparation) to compute session summary metrics (start, end, and average BPM).
- **Silent & Never Shared Externally**: BPM figures are never displayed on the watch screen during meditation, **are never written to Google Health Connect**, and **are never sent to external servers**. They are saved strictly within the local session history on the paired smartphone.
- **Fully Optional**: If the user denies or revokes `BODY_SENSORS`, the timer and all meditation features on Wear OS continue working normally without restriction.

### 3. Local Data Storage & Backups
- **Settings & Preferences**: User configurations on phone and watch (such as timer duration, visual style, bell sound and volume, interval chime settings, Auto Do Not Disturb on smartphone, and color theme) are stored locally in Android's private `SharedPreferences`.
- **Session History**: Meditation logs (including any optional BPM values recorded from the watch) are saved solely on the local device. Users can manually delete any session record from the history screen at any time.
- **Manual JSON Backup (Export / Import)**: From the smartphone history screen, users can manually export or import their complete session history in **JSON** format via Android's system file picker (Storage Access Framework). Exported JSON files include any BPM data saved in local history and are written only to the location chosen by the user; duplicate sessions are automatically skipped on import.
- **Android System Backup**: If the user has enabled device backup in Android settings, app preferences and local session history may be included in Google's encrypted system backup service (Google Drive or direct device-to-device transfer).

### 4. Watch and Phone Communication (Wear OS)
Communication between the Wear OS watch and paired Android phone takes place locally and securely via official Google Play Services (`Wearable Data Layer`) over Bluetooth or local Wi-Fi. This channel is strictly limited to **transferring completed meditation sessions from the watch to the phone** (duration, start/end timestamps, and any optional BPM metrics recorded during the session) for local history logging and writing the mindfulness session to Health Connect. No automatic synchronization of app settings occurs between devices.

### 5. User Rights and Data Deletion
- Users can revoke Health Connect permissions at any time via Android *Settings > Security & Privacy > Health Connect*.
- The `BODY_SENSORS` permission on Wear OS can be granted or revoked at any time via watch *Settings > Apps > Permissions > Sensors*.
- Sessions saved to Health Connect can be viewed or deleted at any time through the Android Health Connect system interface.
- Uninstalling the application deletes all locally stored preferences and history from the device (unless subsequently restored via Android's Google system backup or a user-exported JSON backup file).

### 6. Contact
For any inquiries regarding this policy or the application source code, please open an issue on the project GitHub repository:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

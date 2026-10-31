# Privacy Policy - Still (Informativa sulla Privacy)

*Ultimo aggiornamento / Last updated: 29 Ottobre 2026*

---

## 🇮🇹 Versione Italiana

Questa informativa sulla privacy descrive come l'applicazione **Still** (sia per smartphone Android che per smartwatch Wear OS) gestisce i dati dell'utente, con particolare riferimento ai dati relativi al benessere e all'integrazione con **Google Connessione Salute (Health Connect)**.

### 1. Filosofia e Trasparenza
**Still** è un'applicazione open source per la meditazione progettata nel pieno rispetto della privacy dell'utente:
- **Nessun account esterno richiesto**: non è richiesta alcuna registrazione o creazione di profilo.
- **Nessun server proprietario o cloud**: Still non possiede server e non trasmette alcun dato su server remoti o di terze parti.
- **Nessuna pubblicità o tracker**: l'app non contiene annunci pubblicitari, librerie di profilazione o strumenti di tracciamento o analytics di terze parti.

### 2. Dati di Google Connessione Salute (Health Connect)
Still si integra con Google Connessione Salute per permettere agli utenti di registrare le proprie sessioni di meditazione.

- **Permessi richiesti**: Still richiede esclusivamente il permesso di **scrittura** per le sessioni di meditazione/mindfulness (`WRITE_MINDFULNESS` / `MindfulnessSessionRecord`).
- **Nessuna lettura di dati sanitari**: Still **non legge**, non accede e non monitora alcun altro dato personale o sanitario presente nel tuo account Google Salute (come frequenza cardiaca, sonno, calorie, passi, ecc.).
- **Scopo dell'utilizzo**: L'accesso in scrittura viene utilizzato unicamente per salvare data, orario di inizio, orario di fine e durata della sessione di meditazione completata, al solo fine di mantenere aggiornato il registro del benessere dell'utente.
- **Condivisione**: I dati inviati a Health Connect rimangono gestiti direttamente da Google e dalle policy di sicurezza del sistema operativo Android. Still non vende, non condivide e non trasferisce tali dati a terzi.

### 3. Dati Memorizzati sul Dispositivo
- **Preferenze e Impostazioni**: preferenze dell'app (es. durata del timer, intervallo campane, frequenza del gong, attivazione automatica Non Disturbare) vengono salvate localmente nella memoria protetta del dispositivo tramite `SharedPreferences`.
- **Storico Locale**: l'elenco delle sessioni completate viene conservato esclusivamente in locale sul dispositivo. L'utente può eliminare in qualsiasi momento le singole sessioni dallo storico interno dell'app.

### 4. Comunicazione tra Smartphone e Smartwatch (Wear OS)
La comunicazione tra l'app per smartwatch (Wear OS) e l'app per smartphone avviene esclusivamente in modo locale e sicuro tramite i servizi ufficiali di Google (`Google Play Services Wearable Data Layer`), via Bluetooth o rete locale sicura. I dati scambiati riguardano solo la sincronizzazione delle impostazioni del timer e il trasferimento delle sessioni completate dall'orologio allo smartphone per l'archiviazione locale e l'invio a Health Connect.

### 5. Controllo dell'Utente e Cancellazione dei Dati
- L'utente ha il pieno controllo sui permessi di Health Connect, che possono essere revocati in qualsiasi momento tramite le *Impostazioni di Sistema Android > Sicurezza e Privacy > Connessione Salute*.
- Le sessioni registrate su Health Connect possono essere visualizzate o eliminate direttamente dall'app di sistema Health Connect / Google Salute.
- La disinstallazione dell'applicazione Still comporta la cancellazione automatica di tutti i dati memorizzati localmente dall'app.

### 6. Contatti
Per domande relative alla privacy o al codice sorgente dell'applicazione, è possibile aprire una segnalazione (issue) direttamente sul repository GitHub del progetto:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

---

## 🇬🇧 English Version

This Privacy Policy describes how the **Still** application (for Android smartphones and Wear OS smartwatches) handles user data, particularly concerning wellness data and integration with **Google Health Connect**.

### 1. Core Principles
**Still** is an open-source meditation timer built with privacy as a foundational principle:
- **No Account Required**: No account creation, login, or personal profile is needed.
- **No Cloud Servers**: Still does not operate remote servers and never transmits user data over the internet to any external backend.
- **Zero Ads & Trackers**: The application contains no advertisements, analytics SDKs, tracking libraries, or profiling services.

### 2. Google Health Connect Data
Still integrates with Google Health Connect solely to allow users to sync their meditation sessions.

- **Permissions Requested**: Still exclusively requests **write-only** permission for mindfulness/meditation sessions (`WRITE_MINDFULNESS` / `MindfulnessSessionRecord`).
- **No Read Access**: Still **never reads**, accesses, or inspects any other personal health records (such as heart rate, sleep data, step counts, etc.).
- **Data Usage Purpose**: Write permission is used exclusively to record the start time, end time, and duration of completed meditation sessions in Health Connect.
- **Data Sharing**: Data written to Health Connect is managed by Google according to Android's privacy framework. Still does not sell, share, or disclose health data to any third party.

### 3. Local Data Storage
- **Settings & Preferences**: User configurations (such as timer durations, gong sound frequencies, interval chime settings, and Do Not Disturb preferences) are stored locally on the device using Android's private `SharedPreferences`.
- **Session History**: Meditation logs are saved solely on the local device. Users can manually delete any session record from the history screen at any time.

### 4. Watch and Phone Communication (Wear OS)
Communication between the Wear OS watch and paired Android phone takes place locally and securely via official Google Play Services (`Wearable Data Layer`) over Bluetooth or local Wi-Fi. This channel is strictly limited to syncing timer preferences and transferring finished watch sessions to the phone for local logging and Health Connect export.

### 5. User Rights and Data Deletion
- Users can revoke Health Connect permissions at any time via Android *Settings > Security & Privacy > Health Connect*.
- Sessions saved to Health Connect can be viewed or deleted at any time through the Android Health Connect system interface.
- Uninstalling the application deletes all locally stored preferences and history from the device.

### 6. Contact
For any inquiries regarding this policy or the application source code, please open an issue on the project GitHub repository:
[https://github.com/sanluca/still](https://github.com/sanluca/still)

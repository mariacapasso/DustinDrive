# Introduzione
Il progetto consiste nella progettazione e sviluppo di un'applicazione Android che comunica tramite Bluetooth con un coprivolante dotato di sensori per monitorare lo stato del guidatore.
L'applicazione ha richiesto l'implementazione delle seguenti funzionalità:
- Scansione e connessione del dispositivo **DUSTIN** ;
- **Ricezione dei pacchetti Bluetooth** trasmessi dal coprivolante;
- **Visualizzazione in tempo reale** delle misure provenienti dal coprivolante;
- **Integrazione di GPS e misure accelerometriche** provenienti dai sensori dello smartphone;
- **Memorizzazione locale dei dati**;
- **Esportazione dei dati memorizzati** in un file .csv
  
# Analisi Dettagliata dei Componenti e delle Classi
<img width="3524" height="2489" alt="image" src="https://github.com/user-attachments/assets/58fc635f-7a32-429b-84d5-4b16ba9c9bdc" />

***Diagramma delle classi***

Il progetto segue l'architettura **MVVM**(Model-View-ViewModel) per garantire modularità e separazione delle responsabilità del codice.
L'applicativo si suddivide in pacchetti che rispecchiano le componenti strutturali del sistema:

### 1. View
Rappresenta lo stato grafico con cui l'utente interagisce direttamente. I **Fragment** sono ospitati all'interno di un'activity (**MainActivity**) che gestisce la navigazione.
Le classi presenti all'interno del package **View** sono:
- **SchermataIniziale** gestisce l'avvio dell'applicazione e la richiesta dei permessi di localizzazione e Bluetooth mediante `requestPermissionLauncher`. 
- **MainActivity** è l'activity host dei 4 fragment principali che gestisce la navigazione tra i vari fragment tramite il metodo `replaceFragment()`.
- **HomeFragment** è il fragment principale mostrato di default che mostra le card dei dati ricevuti in tempo reale (**LiveDataAdapter**) e le card di navigazione (**ItemHomeAdapter**) verso gli altri Fragment tramite appositi adapter dedicati.Osserva `bluetoothManager.connectionState` per mostrare lo stato di connessione del dispositivo **DUSTIN**. 
- **BluetoothFragment** si occupa della ricerca, connessione e disconnessione del dispositivo **DUSTIN**.Si collega a `BluetoothService` per accedere al manager condiviso e guida l'utente nell'attivare Bluetooth e posizione quando necessario.
- **DashBoardFragment** è il pannello di controllo visuale.Visualizza in tempo reale il grafico PPG (libreria **MPAndroidChart**) e i dati di accelerazione/stato guida.Lo stato di guida(sicura/moderata/brusca/pericolosa) viene classificato in base a soglie sull'accelerazione media.
- **MappeFragment** gestisce il tracciamento della posizione geografica corrente e lo mostra sulla mappa.
 
#### Sotto-Pacchetto: `View.Adapter`
Classi che si occupano della gestione della visualizzazione dinamica delle liste all'interno di `RecyclerView`.
* **`DeviceAdapter`** collega i dispositivi Bluetooth rilevati (`BluetoothDevice`) agli elementi grafici della lista di ricerca.
* **`ItemHomeAdapter`** gestisce le card di navigazione nella Home.
* **`LiveDataAdapter`** popola le card nella Home con i dati ricevuti in tempo reale .
* **`ViewHolder`** è una classe interna riutilizzabile (estende `RecyclerView.ViewHolder`) che mantiene i riferimenti ai widget della riga per evitare chiamate ripetute a `findViewById`.Espone elementi come `name`, `stato` e il pulsante `disconnetti`.

### 2. Model
Rappresenta i dati e la logica di dominio dell'applicazione. Sono classi di dati pure senza dipendere dall'interfaccia utente.
Le classi presenti all'interno del package **Model** sono:
- **SyncPacket** è un `object` contenente la funzione `costruisci_pacchetto() : ByteArray` che si occupa della costruzione del pacchetto di sincronizzazione da inviare al dispositivo **DUSTIN** per sincronizzare i tempi sul microcontrollore. Si tratta di un array di 13 byte con timestamp corrente `Instant.now()` a 64 bit e CRC16 calcolato su `Utility.calcola_crc16`.
- **SensorPacket** è una classe che rappresenta i pacchetti grezzi di 12 byte trasmessi dal coprivolante via Bluetooth. Il costruttore richiede esattamente 12 byte(`require`), altrimenti lancia un'eccezione. Il metodo `isValid() : Boolean` verifica se un pacchetto ricevuto sia valido confrontando il CRC ricevuto. Al suo interno vi è la classe `enum class SensorTYPE` che elenca i tipi di sensori supportati col relativo valore byte. `Companion: from(value: Int): SensorTYPE?`:ritorna null se il byte non corrisponde a nessun tipo noto.
- **PacketParser** è un `object` che contiene metodi che consentono di effettuare il parsing dei dati ricevuti come: `startParse(SensorPacket)` che riceve un pacchetto grezzo e ritorna un oggetto `ParsedPacket` tipizzato; `calcolaPercentualeBatteria(Double)` che converte la tensione letta in un valore percentuale; `convertTimeStamp(Long)` che converte il timestamp calcolato in formato `DateTime`. 
- **ParsedPacket** è una `sealed class` che rappresenta i dati tipizzati estratti dai sensori suddivisi nelle strutture interne dedicate: `Battery`, `Touch`, `TAC`, `PPG`.
- **TimeStampSync** è un `object` che contiene metodi che calcolano il tempo effettivo in cui è stato ricevuto il pacchetto. Il metodo `onSyncSent(Long)` viene chiamato quando viene inviato `SyncPacket` la prima volta e calcola il tempo di accensione del microcontrollore;
  `computeTimeStamp()` è la funzione che calcola il timestamp effettivo del pacchetto ricevuto.
- **Utility** contiene il metodo `calcola_crc16(ByteArray)` che calcola l'algoritmo di ridondanza ciclica (CRC16) per verificare che i byte trasmessi dal modulo Bluetooth non abbiano subito corruzioni durante lo stream.
- **LiveDataModel** è una `data class` per le card che mostrano i dati in tempo reale in `HomeFragment` tramite `LiveDataAdapter`.
- **ItemHomeCard** è una `data class` per le card di navigazione nella Home.

#### Persistenza Dati
Per la memorizzazione in locale dei dati è stato utilizzato **Room Database** che è una libreria di persistenza che fornisce uno strato di astrazione sul database **SQLite** per consentire un database più robusto. La scelta è ricaduta su di esso in quanto rende i dati osservabili e aggiorna automaticamente l'interfaccia utente ed inoltre supporta le **Coroutines**, ossia gestisce le query nei thread in background per migliori prestazioni.
Le classi presenti all'interno del package **db** sono:
- **SensorDataEntity** che rappresenta la tabella **Packet** all'interno del database;
- **SensorDataDAO** che contiene i metodi per effettuare operazione sul database come:
   - `addData(SensorDataEntity)`: aggiunge i dati all'interno del database;
   - `getAllData()`: che ritorna tutti i dati presenti nel database. Utile per effettuare l'esportazione di tutti i dati memorizzati.
   - `getDataAfter(lastId: Long)`: ritorna i dati memorizzati successivi al pacchetto con un certo id. Questa funzione viene usata per esportare i dati presenti nel database in seguito alla prima esportazione, ossia non esporta tutto il database, ma parte dall'ultimo pacchetto che ha già esportato.
- **SensorDataRepository** funge da intermediario che astrae l'accesso al database.Il ViewModel non parla mai direttamente con il DAO, ma richiede le operazioni al Repository, garantendo la possibilità futura di modificare il codice senza toccare la logica utente.
- **SensorDataDB** contiene l'istanza del database e collega le DAO e le entità.

### 3. Controller(View-Model)
Questo livello si occupa della gestione della connessione persistente e della manipolazione asincrona dei flussi di byte.
* **SharedViewModel**: Il nucleo centrale dell'applicazione.Condiviso tra tutti i Fragment tramite `activityViewModels()`, possiede e coordina:
  - **Accelerometro(SensorManager)** che calcola la norma vettoriale dell'accelerazione e ne mantiene la media su finestre temporali di 15 secondi;
  - **GPS(FusedLocationProviderClient)** che aggiorna la posizione attuale dell'utente ogni secondo;
  - **Flusso di dati Bluetooth**: delega il parsing a `PacketParser` e salva i pacchetti(eccetto quelli PPG) tramite `SensorDataRepository`.
    Uno dei metodi principali presenti in questa classe è `exportedDataToCsvFile()` che si occupa di esportare i dati non ancora esportati,presenti nel database, in un file `.csv` tenendo traccia di un cursore(`lats_exported_id`) salvato in `SharedPreferences`.
* **BluetoothConnectionManager**: Un manager strutturato come Singleton che incapsula la logica delle API Bluetooth Android. Gestisce il ciclo di vita del `BluetoothSocket`, monitora gli stati di connessione (`ConnectionState`) e orchestra i tentativi di riconnessione automatica.
  Metodi principali: - `startDiscovery()` / `stopDiscovery()`: scansione dispositivi tramite `BroadcastReceiver` in ascolto su `ACTION_FOUND`;
    - `connect(device)`: apre un `socket RFCOMM` e avvia la lettura continua dei dati (`listen()`);
    - `disconnect()`:chiusura esplicita della connessione;
    - `reconnect()`: tentativo automatico di riconnessione in caso di errore di comunicazione, con numero di tentativi (5) e intervallo configurabili;
    - `sendPacket()`: invia un `SyncPacket` per la sincronizzazione orologio. Il pacchetto di sincronizzazione viene inviato ogni 30s.
Al suo interno vi è la `sealed class ConnactionState()` che modella lo stato di connessione Bluetooth, osservato come `StateFlow` sia dai Fragment che dal ViewModel.
* **BluetoothService**: **Foreground service** che mantiene attiva l'istanza di `BluetoothConnectionManager` anche quando l'app passa in background, mostrando una notifica persistente (silenziosa) all'utente. Viene avviato dalla **schermata Bluetooth** nel momento in cui l'utente vi accede, e resta attivo anche dopo che l'utente naviga verso altre schermate o disconnette il dispositivo, garantendo continuità nella raccolta dati a condizione che l'utente sia passato almeno una volta dalla schermata Bluetooth durante la sessione corrente.

# Tecnologie Utilizzate 
- Linguaggio di programmazione usato per lo sviluppo dell'applicazione : **Kotlin** .
- Architettura : **Android Jetpack** per la gestione reattiva dello stato e del ciclo di vita.
- Persistenza Dati:**Room Database** (Astrazione thread-safe sopra SQLite).  
- Concorrenza e Asincronia: **Kotlin Coroutines** (gestione asincrona dei thread di I/O di rete e database).  
- Geolocalizzazione: **Google Play Services Location Framework** (`FusedLocationProviderClient`) per il tracciamento geospaziale ad alta precisione.
- Grafici: **MPAndroidChart** (utilizzata per il rendering dei flussi PPG continui in tempo reale).
- Hardware Framework: - **Android Bluetooth Classic API** (Comunicazione tramite RFCOMM Socket, BluetoothAdapter);
- **Foreground Service** per mantenere attiva la connessione anche con l'app in background.
- Esportazione file: **MediaStore API** (Scrittura del file CSV nella cartella **Downloads** dello smartphone).
- Mappe e Visualizzazione Geospaziale: **osmdroid (OpenStreetMap-Based Map Toolkit per Android)**. Questa libreria open-source viene utilizzata all'interno del **MappeFragment** per renderizzare mappe interattive in modo completamente autonomo, gestire il caricamento dinamico dei tasselli (tile) grafici, posizionare marker personalizzati sulla posizione corrente dell'utente senza dipendere dalle API proprietarie di Google Maps.

# Gestione dei Permessi di Sistema
 L'applicazione richiede una gestione granulare e condizionale dei permessi, differenziata in base alla versione del sistema operativo Android installata sul dispositivo dell'utente, per garantire la compatibilità retroattiva.

### 1. Dispositivi con Android 11 o inferiori (API $\le$ 30)
Sulle versioni legacy di Android, l'accesso all'hardware Bluetooth e la scansione dei dispositivi erano strettamente legati ai permessi di geolocalizzazione.
- `BLUETOOTH`: Consente all'applicazione di connettersi ai dispositivi Bluetooth accoppiati.
- `BLUETOOTH_ADMIN`: Permette all'app di avviare il discovery (ricerca) di nuovi dispositivi e di modificare le impostazioni Bluetooth.
- `ACCESS_FINE_LOCATION`: Richiesto obbligatoriamente su queste API per poter ricevere i risultati della scansione dei dispositivi nell'etere.

### 2. Dispositivi con Android 12 o superiori (API $\ge$ 31)
A partire da Android 12, Google ha separato nettamente i permessi di scansione Bluetooth da quelli della posizione geografica, introducendo i permessi di runtime dedicati.
- `BLUETOOTH_SCAN`: Permette di cercare i dispositivi nelle vicinanze. Nel Manifest è configurato con il flag **android:usesPermissionFlags="neverForLocation"**, il che dichiara esplicitamente al sistema operativo che l'app non usa la scansione Bluetooth per tracciare la posizione geografica dell'utente.
- `BLUETOOTH_CONNECT`: Permette all'applicazione di stabilire la connessione **RFCOMM (Socket)** e interagire con il sensore accoppiato.

### 3. Servizi in Background ed Esportazione (Tutte le API supportate)
- `ACCESS_COARSE_LOCATION`: Utilizzato per il tracking generale e il corretto funzionamento del modulo mappe GPS integrato.
- -`INTERNET e ACCESS_NETWORK_STATE`: Consentono le operazioni di rete e il controllo dello stato di connettività internet dello smartphone. Necessario per la visualizzazione della mappa in **MappeFragment**
- `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_CONNECTED_DEVICE`: Permessi strutturali introdotti nelle API recenti di Android, obbligatori per mantenere attivo il BluetoothService come servizio in primo piano categorizzato per dispositivi connessi. Senza di essi, il sistema operativo killerebbe l'applicazione non appena lo schermo si spegne.
- `WRITE_EXTERNAL_STORAGE`: Utilizzato per garantire la compatibilità sui dispositivi più vecchi quando viene invocato il metodo `exportDataToCsvFile()` per la scrittura fisica del report dei sensori nella memoria locale.

# Guida all'Installazione e Configurazione

## Prerequisiti Hardware e Software
1. **Ambiente di Sviluppo** : Android Studio,
2. **Android SDK**: Compilazione impostata su API Livello 33 (Android 14), con compatibilità minima a partire da API Livello 26 (Android 8.0).
3. **Dispositivo di Test**: È caldamente consigliato l'utilizzo di un dispositivo Android fisico dotato di modulo Bluetooth attivo. Gli emulatori standard di Android Studio non supportano la simulazione nativa del Bluetooth Classic (RFCOMM Socket). Nel caso specifico di questo progetto è stato utilizzato  lo smartphone **Xiaomi Mi 11 Lite**(Android 13) per testarne il funzionamento.

## Procedura per l'Esecuzione dell'Applicativo

#### Passo 1: Clonazione del Repository
Apri il terminale della tua macchina locale o l'interfaccia Git integrata nel tuo IDE ed esegui il comando di clonazione:
`git clone https://github.com/mariacapasso/nome-del-repository.git`.

#### Passo 2: Apertura e Sincronizzazione Gradle
1. Avvia Android Studio.
2. Seleziona `File -> Open` e scegli la cartella radice del progetto appena clonato.
3. Attendi che Android Studio completi l'indicizzazione dei file e la sincronizzazione del build tool. Assicurati che la macchina sia connessa a Internet per consentire il download automatico delle dipendenze dichiarate (Room, Google Play Services, MPAndroidChart).

#### Passo 3: Configurazione dei Permessi sul Dispositivo Fisico
1. Collega lo smartphone Android al computer tramite cavo USB.
2. Abilita le **Opzioni Sviluppatore** sul telefono e attiva il Debug USB.
3. Seleziona il tuo dispositivo fisico dal menu a tendina dei target di esecuzione in alto su Android Studio.

#### Passo 4: Build e Compilazione (Run)
1. Clicca sul pulsante verde **Run ('app')** nella barra degli strumenti superiore.
2. Android Studio compilerà il codice sorgente generando il pacchetto .apk e lo installerà sul dispositivo.
3. Al primo avvio dell'applicazione sul telefono (nella schermata DUSTIN Drive), compariranno a schermo i pop-up di sistema per la richiesta dei permessi. Accetta **esplicitamente tutti i permessi richiesti** (Dispositivi nelle vicinanze/Bluetooth e Posizione).

#### Passo 5: Test di Connessione con il Sensore
1. Assicurati che il dispositivo **DUSTIN** sia acceso e in modalità di trasmissione.
2. Naviga nella sezione **Bluetooth** dell'applicazione **DUSTIN Drive** dall'interfaccia utente.
3. Clicca su **"Ricerca Dispositivi"**: non appena il nome del sensore compare nella lista della RecyclerView, selezionalo per stabilire l'accoppiamento hardware. I grafici nella Dashboard inizieranno a popolare i dati in tempo reale. Una volta che l'utente si disconnetterà al dispositivo, verrà visualizzato un Toast sia nella schermata **Bluetooth** sia nella schermata **Home** di avvenuta esportazione dei dati ricevuti e memorizzati nel database.
4. Andare nei **Downloads** dello smartphone per visualizzare i dati esportati nel file `.csv`.

# P-DMX Controller

P-DMX Controller è un'applicazione Android professionale progettata per il controllo di luci ed effetti tramite il protocollo **Art-Net (DMX over IP)**. L'app trasforma il tuo dispositivo Android in una console luci versatile per piccoli e medi spettacoli, installazioni e test di illuminazione.

## 🚀 Caratteristiche Principali

- **Protocollo Art-Net:** Invio di dati DMX 512 tramite rete Wi-Fi a nodi Art-Net compatibili.
- **Gestione Show:** Crea, salva e carica diversi file show.
- **Patching Flessibile:** Supporto per fixture multi-canale con una libreria integrata e la possibilità di creare profili personalizzati.
- **Scene & Cue List:** Registra stati DMX in Cue, organizza sequenze con tempi di Fade (transizione) personalizzabili.
- **Modalità Live & Edit:** Passa rapidamente dalla programmazione al controllo dal vivo.
- **Grand Master:** Controllo globale dell'intensità per tutti i canali dimmer patchati.
- **Interfaccia Moderna:** Sviluppata interamente con **Jetpack Compose** per un\'esperienza utente fluida e reattiva.

## 🛠 Tech Stack

- **Linguaggio:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architettura:** MVVM (Model-View-ViewModel)
- **Connettività:** UDP Socket per trasmissione Art-Net (Servizio in Foreground per mantenere la connessione attiva).
- **Storage:** Gestione file JSON per il salvataggio degli show.

## 📱 Requisiti

- Dispositivo Android con Android 8.0 (Oreo) o superiore.
- Connessione Wi-Fi sulla stessa rete del nodo Art-Net.

## 🔧 Configurazione

1. Collega il tuo dispositivo alla rete Wi-Fi del tuo controller Art-Net.
2. Apri l'app e verifica lo stato della connessione (l'indicatore mostrerà se il controller è raggiungibile).
3. Vai nella sezione **Patch** per aggiungere le tue luci (Dimmer, Teste Mobili, LED RGB, ecc.).
4. Inizia a programmare i fader e registra le tue **Cue**.

## 🤝 Contribuire

I contributi sono benvenuti! Se hai suggerimenti o bug da segnalare, apri una *Issue* o invia una *Pull Request*.

---
Sviluppato da **paliddo**

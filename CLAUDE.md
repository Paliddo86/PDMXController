# PDMXController - Regole per l'assistente AI

## Memory Bank
Tutta la documentazione del progetto si trova nella directory `.memory/`:
- `.memory/project_overview.md` — Panoramica generale
- `.memory/architecture.md` — Architettura MVVM, package structure
- `.memory/data_model.md` — Data class, serializzazione JSON
- `.memory/connection_layer.md` — Connessione WiFi, Art-Net, handshake
- `.memory/ui_layer.md` — UI, layout, componenti
- `.memory/todo_list.md` — TODO con stato completato/pendente

## Regole obbligatorie

### DOPO OGNI MODIFICA AL CODICE, DEVI:
1. **Identificare** quali file `.memory/*` sono impattati dalla modifica
2. **Aggiornare** quei file con le nuove informazioni
3. **NON modificare** file `.memory/*` che non sono impattati

### Esempi di quando aggiornare:
- **Nuovo file creato** → aggiorna `architecture.md` (package structure)
- **Nuova data class** → aggiorna `data_model.md`
- **Modifica al sistema di connessione** → aggiorna `connection_layer.md`
- **Modifica UI** → aggiorna `ui_layer.md`
- **Feature completata** → sposta da 🔲 a ✅ in `todo_list.md`
- **Nuova feature identificata** → aggiungi a 🔲 in `todo_list.md`
- **Bug fix significativo** → aggiungi a ✅ Bug Fixes in `todo_list.md`

### Quando usare i memory file:
- All'inizio di ogni sessione, leggi `todo_list.md` per capire cosa fare
- Leggi `architecture.md` per capire dove inserire nuovo codice
- Leggi `data_model.md` per capire le strutture dati esistenti

## Stile e convenzioni
- Usa Kotlin idioms (nullable types, coroutines, flow)
- Jetpack Compose per UI (Material3)
- MVVM con StateFlow
- Commenti in italiano nel codice
- Preferisci funzioni pure e immutabilità
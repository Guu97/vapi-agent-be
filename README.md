# vapi-agent-be

Backend Spring Boot per il **voicebot municipale del Comune di Impruneta** — integrato con [Vapi.ai](https://vapi.ai).

Il servizio espone:
- **Webhook Vapi** (5 tool) che il modello vocale invoca in tempo reale durante la chiamata
- **Pipeline RAG** per indicizzare le pagine del sito comunale e rispondere con dati aggiornati
- **CRUD appuntamenti** (prenotazione, cancellazione, ricerca)

---

## Stack tecnico

| Componente | Versione / Dettaglio |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| PostgreSQL | Supabase (pooler PgBouncer, porta 6543) |
| pgvector | `extensions.vector(768)` — indice HNSW, distanza coseno |
| OpenAI Embeddings | `text-embedding-3-small`, 768 dimensioni |
| Scraping HTML | Jsoup 1.17.2 |
| Build | Maven 3 |

---

## Struttura del progetto

```
src/main/java/com/impruneta/vapiagent/
│
├── VapiAgentBeApplication.java        # Entry point (@SpringBootApplication)
│
├── common/
│   ├── ChecksumUtil.java              # SHA-256 per deduplicazione contenuti
│   └── JobResult.java                 # Record di risultato pipeline (processed/skipped/failed)
│
├── servicetype/                       # Tipi di servizio comunali
│   ├── ServiceType.java               # Entità JPA (code, name, description, active)
│   ├── ServiceTypeRepository.java
│   └── ServiceTypeSeeder.java         # Seed iniziale idempotente (@PostConstruct)
│
├── appointment/                       # Gestione appuntamenti
│   ├── Appointment.java               # Entità JPA con soft-delete
│   ├── AppointmentStatus.java         # Enum BOOKED | CANCELLED
│   ├── AppointmentRepository.java
│   ├── AppointmentBookingRequest.java # DTO di prenotazione con validazione Bean
│   ├── AppointmentService.java
│   └── AppointmentController.java     # REST /appointments
│
├── municipaldocument/                 # Documenti comunali scraped
│   ├── MunicipalDocument.java         # Entità: url, title, rawHtml, rawText, checksum
│   ├── MunicipalDocumentRepository.java
│   ├── MunicipalDocumentChunk.java    # Chunk testuale (senza embedding mappato)
│   └── MunicipalDocumentChunkRepository.java
│
├── rag/
│   ├── ingestion/                     # Fase 1: scraping
│   │   ├── SeedUrl.java               # Record (url, serviceType, enabled)
│   │   ├── SeedRegistry.java          # Lista hardcoded di 10 URL del sito comunale
│   │   ├── HtmlFetcher.java           # Download pagina con Jsoup
│   │   ├── HtmlExtractor.java         # Estrazione testo con fallback su 8 selettori CSS
│   │   └── IngestionService.java      # Orchestrazione + checksum dedup
│   │
│   ├── chunking/                      # Fase 2: suddivisione in chunk
│   │   ├── ChunkingProperties.java    # @ConfigurationProperties app.rag.chunking.*
│   │   ├── ChunkingTransactionHelper.java  # REQUIRES_NEW per atomicità per-documento
│   │   └── ChunkingService.java
│   │
│   ├── embedding/                     # Fase 3: vettorizzazione
│   │   ├── EmbeddingService.java      # Interfaccia float[] embed(String)
│   │   ├── OpenAiEmbeddingService.java # Chiamata REST a /v1/embeddings
│   │   ├── EmbeddingVectorRepository.java # JdbcTemplate UPDATE embedding = ?::vector
│   │   └── EmbeddingOrchestrationService.java # Loop chunk-by-chunk, try/catch per chunk
│   │
│   └── retrieval/                     # Fase 4: ricerca semantica
│       ├── RetrievalResult.java       # Record risultato (content, score, sourceUrl…)
│       └── RetrievalService.java      # Cosine similarity via JdbcTemplate + <=> operator
│
└── vapi/
    ├── dto/
    │   ├── VapiToolCallRequest.java   # DTO inbound Vapi (arguments come JsonNode)
    │   └── VapiToolCallResponse.java  # DTO risposta Vapi
    ├── VapiToolsAdapterService.java   # Parsing argomenti + deleghe ai servizi interni
    ├── VapiToolsController.java       # 5 endpoint POST per i tool Vapi
    └── AdminRagController.java        # Endpoint admin per eseguire la pipeline RAG
```

---

## Variabili d'ambiente

| Variabile | Descrizione | Esempio |
|---|---|---|
| `DB_USERNAME` | Utente PostgreSQL / Supabase | `postgres.xxxxx` |
| `DB_PASSWORD` | Password database | — |
| `OPENAI_API_KEY` | Chiave API OpenAI (embedding) | `sk-proj-...` |

> **Non committare mai credenziali reali.** Il file `application.yml` contiene valori di fallback solo per sviluppo locale; rimuoverli prima del deploy.

---

## Avvio locale

```bash
# 1. Clona il repository
git clone <repo-url>
cd vapi-agent-be

# 2. Esporta le variabili d'ambiente
export DB_USERNAME=...
export DB_PASSWORD=...
export OPENAI_API_KEY=...

# 3. Compila e avvia
./mvnw spring-boot:run
```

Il server parte sulla porta `8080` di default.

---

## Pipeline RAG

Esegui i passi nell'ordine indicato. Tutti gli endpoint sono sincroni e bloccanti.

```bash
# 1. Scraping delle pagine del sito comunale
POST /admin/rag/ingest

# 2. Suddivisione dei documenti in chunk (target ~800 caratteri)
POST /admin/rag/chunk

# 3. Generazione embedding con OpenAI (richiede OPENAI_API_KEY valida)
POST /admin/rag/embed

# 4. Test ricerca semantica
GET /admin/rag/retrieve?query=orari+anagrafe&topK=5
```

### URL indicizzati (`SeedRegistry`)

| URL | Servizio |
|---|---|
| Homepage + servizi + orari + segnalazioni | URP |
| Anagrafe (pagina servizio + prenotazioni) | ANAGRAFE |
| Tributi + TARI | TRIBUTI |
| Servizi sociali | SERVIZI_SOCIALI |

---

## Endpoint REST

### Appuntamenti — `/appointments`

| Metodo | Path | Descrizione |
|---|---|---|
| `POST` | `/appointments` | Prenota un appuntamento |
| `POST` | `/appointments/{id}/cancel` | Cancella per ID |
| `GET` | `/appointments/{id}` | Recupera per ID |
| `GET` | `/appointments?email=` | Lista per email cittadino |

### Tool Vapi — `/api/vapi/tools`

Questi endpoint ricevono i webhook di Vapi durante la chiamata vocale.

| Metodo | Path | Tool Vapi |
|---|---|---|
| `POST` | `/api/vapi/tools/retrieve-municipal-info` | Ricerca semantica nel knowledge base |
| `POST` | `/api/vapi/tools/book-appointment` | Prenotazione appuntamento |
| `POST` | `/api/vapi/tools/check-appointment-availability` | Verifica disponibilità slot |
| `POST` | `/api/vapi/tools/get-appointments-by-name` | Ricerca appuntamenti per nome |
| `POST` | `/api/vapi/tools/cancel-appointment` | Cancellazione per slot (email + data + servizio) |

---

## Configurazione Vapi Dashboard

Per ogni tool, crea una nuova voce in **Vapi → Tools → New Tool** con il seguente schema base:

```json
{
  "name": "retrieve-municipal-info",
  "server": { "url": "https://YOUR_HOST/api/vapi/tools/retrieve-municipal-info" },
  "parameters": {
    "type": "object",
    "properties": {
      "query": { "type": "string" },
      "topK":  { "type": "integer" }
    },
    "required": ["query"]
  }
}
```

Gli schemi completi con descrizioni in italiano sono documentati come commenti Javadoc in [src/main/java/com/impruneta/vapiagent/vapi/VapiToolsAdapterService.java](src/main/java/com/impruneta/vapiagent/vapi/VapiToolsAdapterService.java).

---

## Schema database

Lo schema PostgreSQL è **gestito esternamente** (`ddl-auto=none`). Le tabelle attese sono:

| Tabella | Descrizione |
|---|---|
| `service_type` | Tipi di servizio comunale (seed: ANAGRAFE, TRIBUTI, URP, SERVIZI_SOCIALI) |
| `appointment` | Appuntamenti con soft-delete e status BOOKED/CANCELLED |
| `municipal_document` | Documenti scraped (url unico, checksum, rawText) |
| `municipal_document_chunk` | Chunk testuali con colonna `embedding extensions.vector(768)` |

La colonna `embedding` usa un indice HNSW con distanza coseno:

```sql
CREATE INDEX ON municipal_document_chunk
  USING hnsw (embedding extensions.vector_cosine_ops);
```

---

## Note tecniche

- **PgBouncer / Supabase pooler**: il parametro `prepareThreshold=0` nella JDBC URL disabilita i prepared statement lato server, obbligatori in modalità _transaction mode_.
- **pgvector senza Hibernate**: tutte le operazioni sul vettore usano `JdbcTemplate` direttamente (`?::vector` cast) per evitare incompatibilità di tipo con l'ORM.
- **Timezone**: il JVM deve girare in UTC per evitare offset sull'ora degli appuntamenti (colonna `time` PostgreSQL senza timezone). Aggiungere `-Duser.timezone=UTC` agli argomenti JVM in produzione oppure applicare `TimeZone.setDefault(UTC)` nel `@PostConstruct` dell'application class.
- **Formato argomenti Vapi**: in produzione Vapi codifica `arguments` come stringa JSON (`"{\"key\":\"val\"}"`), in modalità test come oggetto JSON diretto. L'`VapiToolsAdapterService` gestisce entrambi i formati.

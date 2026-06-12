# vapi-agent-be

Monorepo for the **municipal voicebot for the Municipality of Impruneta**, integrated with [Vapi.ai](https://vapi.ai).

The system is composed of:
- **Spring Boot backend** — REST APIs, Vapi webhooks, RAG pipeline, conversation logs
- **Frontend** — Static dashboard (vanilla HTML/JS) to view appointments and call logs
- **Nginx** — Reverse proxy that serves the frontend and routes `/api/` calls to the backend

---

## Repository structure

```text
/
├── backend/                     # Spring Boot application
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                    # Static dashboard (vanilla HTML/JS)
│   ├── index.html
│   ├── app.js
│   ├── runtime-config.js        # Runtime configuration (BASE_URL, etc.)
│   └── Dockerfile
├── nginx/
│   └── nginx.conf               # Reverse proxy config
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Technical stack

| Component | Version / Detail |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| PostgreSQL | Supabase (PgBouncer pooler, port 6543) |
| pgvector | `extensions.vector(768)` — HNSW index, cosine distance |
| OpenAI Embeddings | `text-embedding-3-small`, 768 dimensions |
| HTML scraping | Jsoup 1.17.2 |
| Build | Maven 3 |

---

## Setup Instructions

These instructions let you run the project locally, verify the main services, and connect the tools to Vapi.

### Prerequisites

Make sure you have the following installed:

- Java 21
- Maven 3
- Docker Desktop with Docker Compose
- A PostgreSQL/Supabase database already set up
- A valid `OPENAI_API_KEY` for the embeddings pipeline
- A Vapi account
- Optional but recommended: ngrok or an equivalent tunnel to expose the backend to Vapi

---

### 1. Clone the repository

```bash
git clone <repo-url>
cd vapi-agent-be
```

---

### 2. Configure environment variables

Copy the example file:

```bash
cp .env.example .env
```

Open `.env` and set at least these variables:

```env
DB_USERNAME=...
DB_PASSWORD=...
OPENAI_API_KEY=...
```

If you use Supabase with the PgBouncer pooler, also verify that the JDBC URL configured in the backend points to the correct pooler port, typically `6543`.

---

### 3. Start with Docker Compose

Start the full stack:

```bash
docker compose up --build
```

Exposed services:

- Frontend + reverse proxy: [http://localhost](http://localhost)
- Backend API behind nginx: [http://localhost/api](http://localhost/api)

To check container status:

```bash
docker compose ps
```

To follow logs:

```bash
docker compose logs -f
```

---

## Backend structure

```text
backend/src/main/java/com/impruneta/vapiagent/
│
├── VapiAgentBeApplication.java         # Entry point (@SpringBootApplication)
│
├── common/
│   ├── ChecksumUtil.java               # SHA-256 for content deduplication
│   └── JobResult.java                  # Pipeline result record (processed/skipped/failed)
│
├── servicetype/                        # Municipal service types
│   ├── ServiceType.java                # JPA entity (code, name, description, active)
│   ├── ServiceTypeRepository.java
│   └── ServiceTypeSeeder.java          # Idempotent initial seed (@PostConstruct)
│
├── appointment/                        # Appointment management
│   ├── Appointment.java                # JPA entity with soft-delete
│   ├── AppointmentStatus.java          # Enum BOOKED | CANCELLED
│   ├── AppointmentRepository.java
│   ├── AppointmentBookingRequest.java  # Booking DTO with Bean Validation
│   ├── AppointmentService.java
│   └── AppointmentController.java      # REST /appointments
│
├── municipaldocument/                  # Scraped municipal documents
│   ├── MunicipalDocument.java          # Entity: url, title, rawHtml, rawText, checksum
│   ├── MunicipalDocumentRepository.java
│   ├── MunicipalDocumentChunk.java     # Text chunk (embedding not directly mapped)
│   └── MunicipalDocumentChunkRepository.java
│
├── rag/
│   ├── ingestion/                      # Phase 1: scraping
│   │   ├── SeedUrl.java                # Record (url, serviceType, enabled)
│   │   ├── SeedRegistry.java           # Hardcoded list of 10 municipal website URLs
│   │   ├── HtmlFetcher.java            # Page download with Jsoup
│   │   ├── HtmlExtractor.java          # Text extraction with fallback across 8 CSS selectors
│   │   └── IngestionService.java       # Orchestration + checksum dedup
│   │
│   ├── chunking/                       # Phase 2: chunk splitting
│   │   ├── ChunkingProperties.java     # @ConfigurationProperties app.rag.chunking.*
│   │   ├── ChunkingTransactionHelper.java  # REQUIRES_NEW for per-document atomicity
│   │   └── ChunkingService.java
│   │
│   ├── embedding/                      # Phase 3: vectorization
│   │   ├── EmbeddingService.java       # Interface float[] embed(String)
│   │   ├── OpenAiEmbeddingService.java # REST call to /v1/embeddings
│   │   ├── EmbeddingVectorRepository.java # JdbcTemplate UPDATE embedding = ?::vector
│   │   └── EmbeddingOrchestrationService.java # Chunk-by-chunk loop, try/catch per chunk
│   │
│   └── retrieval/                      # Phase 4: semantic retrieval
│       ├── RetrievalResult.java        # Result record (content, score, sourceUrl…)
│       └── RetrievalService.java       # Cosine similarity via JdbcTemplate + <=> operator
│
├── calllog/                            # Vapi conversation logs
│   ├── CallLog.java                    # JPA entity (call_log table)
│   ├── CallLogRepository.java
│   ├── CallLogService.java             # Report persistence + recent log retrieval
│   ├── CallLogController.java          # GET /api/call-logs
│   ├── VapiWebhookController.java      # POST /api/vapi/webhook/call-ended
│   └── dto/
│       ├── VapiEndOfCallReportRequest.java  # Vapi webhook payload DTO
│       └── CallLogResponse.java             # Frontend response DTO
│
└── vapi/
    ├── dto/
    │   ├── VapiToolCallRequest.java    # Vapi inbound DTO (arguments as JsonNode)
    │   └── VapiToolCallResponse.java   # Vapi response DTO
    ├── VapiToolsAdapterService.java    # Argument parsing + delegation to internal services
    ├── VapiToolsController.java        # 5 POST endpoints for Vapi tools
    └── AdminRagController.java         # Admin endpoints to run the RAG pipeline
```

---

## Environment variables

| Variable | Description | Example |
|---|---|---|
| `DB_USERNAME` | PostgreSQL / Supabase username | `postgres.xxxxx` |
| `DB_PASSWORD` | Database password | — |
| `OPENAI_API_KEY` | OpenAI API key for embeddings | `sk-proj-...` |

> **Never commit real credentials.** The `application.yml` file may contain fallback values for local development only; remove them before deployment.

---


## RAG pipeline

Run the steps in the indicated order. All endpoints are synchronous and blocking.

```bash
# 1. Scrape municipal website pages
POST /admin/rag/ingest

# 2. Split documents into chunks (target ~800 characters)
POST /admin/rag/chunk

# 3. Generate embeddings with OpenAI (requires a valid OPENAI_API_KEY)
POST /admin/rag/embed

# 4. Test semantic retrieval
GET /admin/rag/retrieve?query=orari+anagrafe&topK=5
```

### Indexed URLs (`SeedRegistry`)

| URL | Service |
|---|---|
| Homepage + services + office hours + reports | URP |
| Registry office (service page + bookings) | ANAGRAFE |
| Taxes + TARI | TRIBUTI |
| Social services | SERVIZI_SOCIALI |

---

## REST endpoints

### Appointments — `/appointments`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/appointments` | Book an appointment |
| `POST` | `/api/appointments/{id}/cancel` | Cancel by ID |
| `GET` | `/api/appointments/{id}` | Retrieve by ID |
| `GET` | `/api/appointments?email=` | List by citizen email |
| `GET` | `/api/appointments/all` | List all active appointments (ordered by creation date DESC) |

### Conversation logs — `/api/call-logs`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/call-logs?limit=20` | Retrieve recent logs (default 20, max 200) |

### Vapi webhook — `/api/vapi/webhook`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/vapi/webhook/call-ended` | Receives the Vapi end-of-call report and persists the log |

### Vapi tools — `/api/vapi/tools`

These endpoints receive Vapi webhooks during the live voice call.

| Method | Path | Vapi tool |
|---|---|---|
| `POST` | `/api/vapi/tools/retrieve-municipal-info` | Semantic search in the knowledge base |
| `POST` | `/api/vapi/tools/book-appointment` | Appointment booking |
| `POST` | `/api/vapi/tools/check-appointment-availability` | Slot availability check |
| `POST` | `/api/vapi/tools/get-appointments-by-name` | Appointment lookup by name |
| `POST` | `/api/vapi/tools/cancel-appointment` | Slot cancellation (email + date + service) |

---

## Vapi Dashboard configuration

For each tool, create a new entry in **Vapi → Tools → New Tool** with the following base schema:

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

The full schemas with Italian descriptions are documented as Javadoc comments in [backend/src/main/java/com/impruneta/vapiagent/vapi/VapiToolsAdapterService.java](backend/src/main/java/com/impruneta/vapiagent/vapi/VapiToolsAdapterService.java).

---

## Database schema

The PostgreSQL schema is **managed externally** (`ddl-auto=none`). The expected tables are:

| Table | Description |
|---|---|
| `service_type` | Municipal service types (seed: ANAGRAFE, TRIBUTI, URP, SERVIZI_SOCIALI) |
| `appointment` | Appointments with soft-delete and BOOKED/CANCELLED status |
| `municipal_document` | Scraped documents (unique URL, checksum, rawText) |
| `municipal_document_chunk` | Text chunks with `embedding extensions.vector(768)` column |
| `call_log` | End-of-call reports received from Vapi (transcript, summary, duration, timestamp) |

The `embedding` column uses an HNSW index with cosine distance:

```sql
CREATE INDEX ON municipal_document_chunk
  USING hnsw (embedding extensions.vector_cosine_ops);
```

---

## Technical notes

- **PgBouncer / Supabase pooler**: the `prepareThreshold=0` parameter in the JDBC URL disables server-side prepared statements, which are required in transaction mode.
- **pgvector without Hibernate**: all vector operations use `JdbcTemplate` directly (`?::vector` cast) to avoid ORM type compatibility issues.
- **Timezone**: the JVM should run in UTC to avoid shifts on appointment times (`time` column in PostgreSQL without timezone). Add `-Duser.timezone=UTC` to JVM arguments in production or apply `TimeZone.setDefault(UTC)` in a `@PostConstruct` method in the application class.
- **Vapi arguments format**: in production, Vapi encodes `arguments` as a JSON string (`"{\"key\":\"val\"}"`); in test mode, it sends a direct JSON object. `VapiToolsAdapterService` handles both formats.

# AI Recommendations with NVIDIA NIM

How VEIL uses NVIDIA NIM to turn a SQL query, its PostgreSQL plan, and the
active schema into readable index advice. Beginner-to-intermediate level.

---

## 1. What changed

The AI Recommendations page used to fall back to a small client-side rule
engine. Now VEIL asks **NVIDIA NIM** for the analysis first:

```text
browser ──POST /api/ai/recommend──► VEIL backend ──► NVIDIA NIM
        ◄── summary, findings, SQL ────────────────
```

The backend holds the API key, builds the prompt, and validates the JSON
the model returns. The browser never sees the key. If NIM is unavailable,
the page keeps working with the Java analysis (`/api/analyze`) instead of
inventing numbers.

---

## 2. Get a key and start VEIL

1. Create an NVIDIA account and an API key at
   [build.nvidia.com](https://build.nvidia.com).
2. Export the key and run the app:

```bash
export NVIDIA_API_KEY=nvapi-...
export DB_PASSWORD=...
sh mvnw spring-boot:run
```

That is the whole setup. The default model is:

```text
nvidia/nemotron-3.5-lightning-30b-a3b
```

NIM exposes an OpenAI-compatible endpoint, so VEIL posts to:

```text
POST https://integrate.api.nvidia.com/v1/chat/completions
Authorization: Bearer $NVIDIA_API_KEY
```

---

## 3. Configuration

`application.properties`:

```properties
veil.ai.nvidia.base-url=${NVIDIA_NIM_BASE_URL:https://integrate.api.nvidia.com/v1}
veil.ai.nvidia.model=${NVIDIA_NIM_MODEL:nvidia/nemotron-3.5-lightning-30b-a3b}
veil.ai.nvidia.api-key=${NVIDIA_API_KEY:}
veil.ai.nvidia.temperature=0.2
veil.ai.nvidia.max-tokens=4096
veil.ai.nvidia.timeout-seconds=60
```

| Variable | Default | Meaning |
| --- | --- | --- |
| `NVIDIA_API_KEY` | empty | Backend key. Unset means "not configured". |
| `NVIDIA_NIM_BASE_URL` | hosted NIM | Change it for a self-hosted NIM container. |
| `NVIDIA_NIM_MODEL` | Nemotron 3.5 Lightning 30B | Any model the endpoint serves. |

This works unchanged with a local NIM container:

```bash
export NVIDIA_NIM_BASE_URL=http://localhost:8000/v1
export NVIDIA_NIM_MODEL=your-served-model-name
```

---

## 4. The two endpoints

### `GET /api/ai/status`

The UI uses this to show whether the backend already has a key.

```json
{
  "configured": true,
  "provider": "nvidia-nim",
  "model": "nvidia/nemotron-3.5-lightning-30b-a3b",
  "baseUrl": "https://integrate.api.nvidia.com/v1"
}
```

No key is ever returned.

### `POST /api/ai/recommend`

```bash
curl -s -X POST http://127.0.0.1:8080/api/ai/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM student WHERE department_id = 1;",
    "plan": {"nodeType": "Seq Scan", "totalCost": 18.4},
    "schema": {"tables": [{"table_name": "student"}]}
  }'
```

```json
{
  "source": "nvidia_nim",
  "provider": "nvidia-nim",
  "model": "nvidia/nemotron-3.5-lightning-30b-a3b",
  "summary": "Sequential scan dominates the plan.",
  "bottlenecks": ["Seq Scan on student"],
  "findings": [{
    "title": "Index department_id",
    "message": "The filter reads every student row.",
    "severity": "high",
    "suggestionSql": "CREATE INDEX idx_student_department ON student (department_id);"
  }],
  "estimatedImprovement": "~70% fewer rows read"
}
```

`POST /api/ai/chat` takes the same context plus `message` and `history`,
and returns `{"reply": "..."}` for the copilot panel.

### Errors

| Status | Meaning |
| --- | --- |
| `400` | `sql`/`message` missing, or the database rejected the query |
| `503` | No NVIDIA key configured (set `NVIDIA_API_KEY`) |
| `502` | NIM rejected the request (bad key, retired model, rate limit) or returned unusable JSON |

The frontend falls back to the Java findings on any of these, with a
notice that names the reason.

---

## 5. Where the key is allowed to live

| Place | Allowed? | Why |
| --- | --- | --- |
| `NVIDIA_API_KEY` environment variable | **Yes, preferred** | Stays on the server. |
| Key typed in the AI Model dialog | Yes, optional | Kept in `sessionStorage` for this tab only; never in `localStorage`. |
| `veil_ai_config` in `localStorage` | No | Only provider and model are stored. |
| Logs, responses, `connections.json` | No | Not written or echoed. |

For a shared machine, use the environment variable and leave the dialog
key blank.

---

## 6. Choosing another model

The AI Model dialog (⚙ **AI Model**) lets you switch models without
editing files. Any model your NVIDIA account can access works, for
example:

```text
nvidia/nemotron-3.5-lightning-30b-a3b   (default, fast)
nvidia/nemotron-3-ultra-550b-a55b       (larger, strongest reasoning)
moonshotai/kimi-k3.1-pro                (coding-focused, 1M context)
mistralai/mistral-large-4-1t-a52b
```

Changing the model in the dialog sends it with each request; restarting
the backend is only needed when you change `NVIDIA_API_KEY` or the base
URL.

### Model retirement (HTTP 410)

NVIDIA retires hosted models regularly, and a retired model answers
`HTTP 410 Gone`. VEIL turns that into a readable message that names the
model and tells you to set `NVIDIA_NIM_MODEL`. To see what the endpoint
serves right now:

```bash
curl -s https://integrate.api.nvidia.com/v1/models \
  | python3 -c 'import json,sys; [print(m["id"]) for m in json.load(sys.stdin)["data"]]'
```

Pick an id from that list and restart (or type it in the dialog). The
models above were live when this document was written; the list is the
source of truth.

---

## 7. Honest limits

- NIM is a language model, not a query planner. VEIL tells it not to
  invent costs, but the prose can still be imperfect; the PostgreSQL
  numbers in the QUERY INFORMATION panel come from `EXPLAIN`.
- The hosted endpoint has rate limits and may be slow on the first call.
- If the model ignores the "JSON only" instruction, VEIL shows its prose
  as a single finding instead of failing.
- The other providers in the dropdown (Gemini, OpenAI) are optional
  fallbacks for the copilot and require their own keys.

# Nada Network - People & Connections API

A lightweight, high-performance in-memory HTTP API built with **Core Java 17** for discovering family and marriage connections through trusted social graphs.

---

## Overview & Architecture

Nada helps families discover prospective marriage connections through mutual acquaintances. In Indian social networks, trusted recommendations naturally operate within **3 degrees of separation** (You $\to$ Mutual Contact $\to$ Trusted Acquaintance $\to$ Candidate). This API stores people, creates mutual connections, and computes the shortest trust paths bounded at 3 hops.

### Key Architectural Decisions
- **Core Java 17 & Lightweight HTTP Server**: Implemented using the JDK's built-in `com.sun.net.httpserver.HttpServer` with Jackson for JSON serialization. No heavy Spring Boot overhead, resulting in cold boot times under **80 ms**.
- **Thread-Safe In-Memory Graph**:
  - `ConcurrentHashMap<Long, Person>` provides $O(1)$ node lookups.
  - `ConcurrentHashMap<Long, Set<Long>>` with `ConcurrentHashMap.newKeySet()` stores adjacency lists, guaranteeing atomic, lock-free edge insertion under concurrent traffic.
  - `AtomicLong` provides thread-safe, monotonic ID generation.
- **Shortest Path via Bounded BFS**:
  - Breadth-First Search (BFS) mathematically guarantees the shortest path on unweighted graphs.
  - Depth-bounded pruning halts expansion as soon as search depth reaches 3 hops, maintaining optimal $O(b^3)$ runtime (where $b$ is average branching factor).
- **First-Class UTF-8 & Regional Scripts**:
  - Full UTF-8 request and response streams to support names in Hindi (e.g. `"आशा"`), Arabic, Punjabi, etc. without character corruption.

---

## Requirements

- **Java Development Kit (JDK)**: Java 17 or newer
- **Maven**: 3.8+ (or Maven 3.9+)

---

## 1. How to Run the Server

### Option A: Using Maven (Development)
```bash
mvn compile exec:java
```

### Option B: Build and Run Fat JAR (Production)
```bash
mvn clean package
java -jar target/nada-api.jar
```

### Default Port & Configuration
- **Default Port**: `8080` (`http://localhost:8080`)
- **Custom Port**: Pass the `PORT` environment variable:
  ```bash
  PORT=9000 java -jar target/nada-api.jar
  ```

---

## 2. How to Run the Tests

Execute the full suite of unit and integration tests (including the mandatory 3-hop boundary verification and HTTP endpoints):

```bash
mvn test
```

### Test Coverage Highlights
- **Mandatory 3-Hop Boundary Test**:
  - Chain `A - B - C - D`: `A to D` $\to$ `connected: true, hops: 3`
  - Chain `A - B - C - D - E`: `A to E` $\to$ `connected: false` (exceeds limit)
- **Same Start and End**: `A to A` $\to$ `connected: true, hops: 0, path: [A]`
- **Hindi Script Roundtrip**: Unicode verification for names such as `"आशा"`
- **Error & Edge Cases**:
  - Blank names $\to$ `400 Bad Request`
  - Connecting `a == b` $\to$ `400 Bad Request`
  - Unknown person IDs $\to$ `404 Not Found` with `{"error": "no person <id>"}`
  - Idempotent duplicate connection handling $\to$ `204 No Content`
  - Missing/non-numeric query parameters $\to$ `400 Bad Request`

---

## 3. API Reference

### 1. Create a Person
- **Endpoint**: `POST /people`
- **Request**:
  ```json
  {"name": "Asha"}
  ```
- **Response**: `201 Created`
  ```json
  {"id": 1, "name": "Asha"}
  ```

### 2. Create Mutual Connection
- **Endpoint**: `POST /knows`
- **Request**:
  ```json
  {"a": 1, "b": 2}
  ```
- **Response**: `204 No Content` *(idempotent on duplicates; 400 if `a == b`)*

### 3. Get Direct Contacts
- **Endpoint**: `GET /people/{id}/contacts`
- **Response**: `200 OK`
  ```json
  [{"id": 2, "name": "Rohan"}]
  ```

### 4. Find Connection Path
- **Endpoint**: `GET /path?from=1&to=5`
- **Response (Connected within 3 hops)**: `200 OK`
  ```json
  {
    "connected": true,
    "hops": 2,
    "path": [
      {"id": 1, "name": "Asha"},
      {"id": 3, "name": "Meera"},
      {"id": 5, "name": "Kabir"}
    ]
  }
  ```
- **Response (Not connected or > 3 hops)**: `200 OK`
  ```json
  {"connected": false}
  ```

---

## 4. What I Would Do Next With More Time

1. **Bidirectional BFS Traversal**:
   - For massive graphs, search concurrently from both source (`from`) and destination (`to`). Bidirectional BFS reduces the search space from $O(b^d)$ to $2 \times O(b^{d/2})$, cutting traversed nodes exponentially.
2. **Persistent Graph Database Integration**:
   - Back the graph with **Neo4j** or **PostgreSQL with recursive CTEs / Apache AGE** for durable storage across restarts.
3. **Relationship Metadata & Edge Attributes**:
   - In matrimonial discovery, context matters. Extend connections with relationship labels (e.g., `FAMILY`, `COLLEAGUE`, `MATCHMAKER`) and weights to score connection strength.
4. **Second-Degree Discovery Recommendations**:
   - Add an endpoint `GET /people/{id}/recommendations` to proactively suggest high-trust 2nd-degree profiles with mutual contact counts.
5. **High-Degree Node (Influencer/Matchmaker) Pruning**:
   - Some matchmakers or community elders might have 5,000+ connections. Implement smart branching heuristics to avoid path explosion through mega-nodes.
6. **Authentication & Rate Limiting**:
   - Add API Key / JWT authentication and token-bucket rate limiting per IP to prevent graph scraping.

---

## 5. AI Tools Disclosure

In accordance with the submission guidelines, AI assistance (Google Antigravity / Gemini) was utilized during development for:
- Drafting boilerplate HTTP handler mappings and test scenarios.
- Reviewing edge cases for Indian matrimonial graph requirements (such as Devanagari UTF-8 preservation).
All architecture decisions, data structure design, and implementation logic were validated and tested.

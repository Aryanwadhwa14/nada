# Nada Network - People & Connections API: Implementation Phases & Git Roadmap

This document outlines the step-by-step roadmap to build the take-home assignment for **Nada Network** (Backend Intern). Each phase corresponds to an atomic, clean Git commit with verifiable milestones.

---

## Git Commit History Strategy

The evaluators specifically review the commit history (`"Commit as you go. We read the commit history."`). 
Follow conventional commit messages: `feat:`, `test:`, `fix:`, `docs:`, `refactor:`.

| Phase | Git Commit Message | Scope / Deliverables |
|:---|:---|:---|
| **Phase 0** | `chore: initialize Java 17 Maven project with dependencies and base config` | `pom.xml`, project structure, Jackson / JUnit 5 dependencies, `.gitignore` |
| **Phase 1** | `feat: domain models and thread-safe in-memory graph repository` | `Person`, `PathResponse`, `ErrorResponse`, `GraphRepository` (ConcurrentHashMap) |
| **Phase 2** | `feat: add POST /people endpoint with UTF-8 and validation support` | ID generator, `name` validation (blank/null), 201 Created, 400 Bad Request |
| **Phase 3** | `feat: add POST /knows and GET /people/{id}/contacts endpoints` | Bidirectional edge creation, idempotent duplicate handling, `a == b` check, 404 for missing IDs |
| **Phase 4** | `feat: implement BFS shortest-path algorithm for GET /path with 3-hop limit` | Max-depth BFS, same start/end (hops=0), 400/404 validations, `{"connected": false}` handling |
| **Phase 5** | `test: add comprehensive unit and integration tests including 3-hop boundary test` | `A-B-C-D` (3 hops) vs `A-B-C-D-E` (exceeds limit), UTF-8 Hindi names, bad JSON, 404s |
| **Phase 6** | `docs: add comprehensive README with execution instructions and AI disclosure` | Run instructions, port config, test commands, future improvements, AI disclosure |

---

## Detailed Phases Breakdown

### Phase 0: Project Setup & Build Configuration
- **Objective**: Establish a clean Java 17 workspace with build tooling.
- **Tech Stack Choices**:
  - Java 17 (LTS)
  - Build Tool: Maven (standard, portable, zero-configuration for reviewers)
  - HTTP Server: Lightweight Java HTTP server (`com.sun.net.httpserver.HttpServer` or Javalin) — boots instantaneously in <200ms without heavy Spring framework overhead.
  - JSON Serializer: Jackson (`jackson-databind`) with full UTF-8 support.
  - Testing: JUnit 5 (`junit-jupiter`) + AssertJ / Mockito / Java `HttpClient` for integration testing.
- **Git Commit**: `chore: initialize Java 17 Maven project with dependencies and base config`

---

### Phase 1: Domain Modeling & In-Memory Graph Storage
- **Objective**: Create clean models and a thread-safe graph storage abstraction.
- **Key Classes**:
  - `Person(long id, String name)`
  - `GraphRepository`:
    - `ConcurrentMap<Long, Person> people`: Stores people by ID.
    - `ConcurrentMap<Long, Set<Long>> adjacencyList`: Stores bidirectional relationships (`ConcurrentHashMap.newKeySet()`).
    - `AtomicLong idCounter`: Thread-safe monotonic ID sequence starting from 1.
- **Git Commit**: `feat: domain models and thread-safe in-memory graph repository`

---

### Phase 2: Person Creation Endpoint (`POST /people`)
- **Objective**: Handle user creation with strict input validation.
- **Specifications**:
  - Route: `POST /people`
  - Status: `201 Created`
  - Validations:
    - Empty/blank name (`""` or `"   "`) -> `400 Bad Request`
    - Missing `name` field -> `400 Bad Request`
    - Invalid JSON payload -> `400 Bad Request`
    - Full UTF-8 support for multilingual names (Hindi, Arabic, emojis, etc.).
- **Git Commit**: `feat: add POST /people endpoint with UTF-8 and validation support`

---

### Phase 3: Connections & Direct Contacts (`POST /knows`, `GET /people/{id}/contacts`)
- **Objective**: Establish mutual links and direct retrieval.
- **Specifications**:
  - Route: `POST /knows` (`{"a": 1, "b": 2}`)
    - Status: `204 No Content`
    - Idempotent: Sending the same connection multiple times succeeds with 204.
    - Self-connection check: If `a == b` -> `400 Bad Request`.
    - Existence check: If `a` or `b` does not exist -> `404 Not Found` with `{"error": "no person <id>"}`.
  - Route: `GET /people/{id}/contacts`
    - Status: `200 OK`
    - If ID does not exist -> `404 Not Found` (`{"error": "no person <id>"}`).
    - Malformed ID -> `400 Bad Request`.
    - Return sorted/consistent list of `Person` objects, or `[]`.
- **Git Commit**: `feat: add POST /knows and GET /people/{id}/contacts endpoints`

---

### Phase 4: Path Finding Endpoint (`GET /path?from=X&to=Y`)
- **Objective**: Implement Breadth-First Search (BFS) bounded at 3 hops.
- **Specifications**:
  - Route: `GET /path?from=1&to=5`
  - Validations:
    - Missing or non-integer `from` / `to` -> `400 Bad Request`.
    - Non-existent `from` or `to` -> `404 Not Found` (`{"error": "no person <id>"}`).
    - `from == to` -> Return `{"connected": true, "hops": 0, "path": [{"id": X, "name": "..."}]}`.
  - Search Strategy:
    - Breadth-First Search (BFS) guarantees the **shortest path** on unweighted graphs.
    - Bounded search: Stop exploring nodes beyond depth 3.
    - If path exists and `hops <= 3`: return `{"connected": true, "hops": N, "path": [...]}`.
    - If disconnected or shortest path > 3 hops: return `{"connected": false}`.
- **Git Commit**: `feat: implement BFS shortest-path algorithm for GET /path with 3-hop limit`

---

### Phase 5: Verification & Mandatory Tests
- **Objective**: Comprehensive test suite testing all edge cases and boundary conditions.
- **Required Boundary Test**:
  - `A - B - C - D`: A to D -> `connected: true, hops: 3`
  - `A - B - C - D - E`: A to E -> `connected: false` (exceeds 3 hops)
- **Additional Test Scenarios**:
  - UTF-8 Hindi name roundtrip: `"आशा"`
  - Duplicate connections idempotency: adding `1 <-> 2` twice
  - Self-loop prevention: `a == b` returns 400
  - Missing and unknown IDs return 404 with exact error format
  - Malformed query params return 400
- **Git Commit**: `test: add comprehensive unit and integration tests including 3-hop boundary test`

---

### Phase 6: Documentation & Submission Preparation
- **Objective**: Create reviewer-friendly README meeting all assignment criteria.
- **Checklist**:
  - [x] Clear port and start instructions (`mvn clean package`, `java -jar ...` or `mvn exec:java`)
  - [x] Test command (`mvn test`)
  - [x] Future architectural improvements (Persistence, Bidirectional BFS, Redis cache, Authentication)
  - [x] AI disclosure statement
  - [x] Invite collaborators: `harshtitoria-dev` and `jordandivyansh`
- **Git Commit**: `docs: add comprehensive README with execution instructions and AI disclosure`

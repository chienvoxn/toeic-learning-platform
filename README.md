# TOEIC Learning Platform

A web-based TOEIC learning platform designed to support exam preparation, vocabulary learning, spaced repetition (SRS), and personalized learning experiences.

**Project status:** Foundation established. Core learning features are under development.

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, React Router 7, Vite 8 |
| Backend | Java 21, Spring Boot 4 |
| Database | PostgreSQL |
| Database migrations | Flyway |
| Persistence | Spring Data JPA / Hibernate |
| Build tools | Maven, npm |
| CI | GitHub Actions |
| Version control | Git / GitHub |

## 2. Project Structure

```text
toeic-learning-platform/
├── .github/
│   └── workflows/
│       └── ci.yml
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/
│   │   └── test/
│   ├── pom.xml
│   └── mvnw.cmd
├── database/
├── docs/
├── frontend-web/
│   ├── src/
│   ├── package.json
│   └── package-lock.json
├── FOUNDATION_INTEGRATION.md
└── README.md
```

## 3. Prerequisites

Install the following tools before running the project:

- Java Development Kit (JDK) 21
- PostgreSQL (a supported version; PostgreSQL 17 is used in CI)
- Node.js 22 (used in CI)
- npm
- Git

The backend includes a Maven Wrapper, so installing Maven globally is optional.

## 4. Database Setup

Start PostgreSQL and create the application database.

Using PostgreSQL SQL Shell (`psql`):

```sql
CREATE DATABASE toeic_db;
```

The default backend database configuration is:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/toeic_db
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}
```

Set the database password as an environment variable.

**Windows PowerShell:**

```powershell
$env:DB_PASSWORD = "YOUR_POSTGRES_PASSWORD"
```

**Linux / macOS:**

```bash
export DB_PASSWORD="YOUR_POSTGRES_PASSWORD"
```

Do not commit database passwords or other credentials to Git.

### Database migrations

Flyway is enabled in the backend configuration:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

Migrations are executed automatically during Spring Boot startup.

The current database migration sequence includes V1–V10, covering the initial schema, identity and authorization, learning content, question bank, test attempts, vocabulary and analytics, AI tutor/RAG, listening practice, system audit, and ERD completion.

Database schema availability does not imply that all corresponding application features are implemented.

## 5. Backend Setup

Navigate to the backend directory:

```powershell
cd backend
```

Set `DB_PASSWORD` in the same terminal session.

Run backend tests and build:

```powershell
.\mvnw.cmd clean verify
```

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend runs at:

`http://localhost:8080`

### Health Checks

Application health:

```http
GET http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "UP",
  "message": "TOEIC Backend is running"
}
```

Database connectivity:

```http
GET http://localhost:8080/api/health/database
```

Expected response:

```json
{
  "status": "UP",
  "database": "toeic_db"
}
```

## 6. Frontend Setup

Open a separate terminal and navigate to the frontend directory:

```powershell
cd frontend-web
```

Install dependencies:

```bash
npm ci
```

Start the development server:

```bash
npm run dev
```

Open the local URL printed by Vite, normally:

`http://localhost:5173`

### Frontend Quality Checks

Run linting:

```bash
npm run lint
```

Build for production:

```bash
npm run build
```

The production build output is generated in `frontend-web/dist/`.

## 7. Testing

### Backend

```powershell
cd backend
.\mvnw.cmd clean verify
```

Backend integration tests require access to a configured PostgreSQL database.

### Frontend

```powershell
cd frontend-web
npm ci
npm run lint
npm run build
```

Passing these checks verifies the current build and test configuration; it does not establish complete functional test coverage.

## 8. Continuous Integration

GitHub Actions runs the CI workflow on pushes and pull requests targeting `master` or `develop`.

**Frontend job:**
- Node.js 22
- `npm ci`
- `npm run lint`
- `npm run build`

**Backend job:**
- Java 21
- PostgreSQL 17 service
- Database `toeic_db`
- `mvn -B clean verify`

Workflow configuration:

`.github/workflows/ci.yml`

## 9. Git Workflow

The project uses the following branch conventions:

- `master`: Main integration/release branch.
- `develop`: Shared development branch.
- `feat/*`: Feature development branches.
- `fix/*`: Bug fixes.
- `docs/*`: Documentation changes.
- `chore/*`: Maintenance and tooling changes.

Create feature branches from the latest `develop` branch.

Example:

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feat/auth-register
```

After implementing and testing a feature, push the branch and create a Pull Request targeting `develop`.

Do not commit directly to `master` for routine feature development.

## 10. Planned Features

The following capabilities are planned or under development:

- User registration and authentication
- Role-based authorization
- TOEIC learning content and question bank
- TOEIC practice tests and attempt tracking
- Vocabulary learning with spaced repetition (SRS)
- Listening practice
- Learning progress and analytics
- AI Tutor and retrieval-augmented learning assistance
- Learner and administrator interfaces

Implementation status should be tracked through GitHub Issues and Pull Requests.

## 11. Documentation

Additional project documentation is maintained in:

- `docs/`
- `database/`
- `FOUNDATION_INTEGRATION.md`

These resources contain design and integration materials for the TOEIC Learning Platform.

## 12. Contributing

Before submitting a Pull Request:

1. Update your branch from `develop`.
2. Implement changes within the assigned scope.
3. Run the relevant backend or frontend checks.
4. Review changed files and avoid committing credentials.
5. Create a Pull Request targeting `develop`.
6. Wait for CI checks and code review before merging.

## 13. License

See the `LICENSE` file for the project's license terms.

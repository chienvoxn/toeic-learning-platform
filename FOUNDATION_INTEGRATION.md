# P1 Foundation integration guide

This is a **patch**, not a replacement repository. Copy its files into the existing repository.

## Scope
- Java package boundaries only (`package-info.java`); no entities, repositories, DB migrations, or Spring Security changes.
- React Router, API client, shared layout, basic pages, feature directories.
- GitHub Actions frontend lint/build and backend tests.

## Safe Git workflow (PowerShell)
```powershell
git status
git fetch origin
git worktree add -b chore/project-foundation ../toeic-foundation origin/develop
# Copy patch contents into ../toeic-foundation (do NOT copy over backend pom.xml or application.properties)
cd ../toeic-foundation
cd frontend-web
npm install
npm run lint
npm run build
cd ..
git add .github frontend-web backend/src/main/java/com/toeic/backend FOUNDATION_INTEGRATION.md
git diff --cached --name-status
git commit -m "chore: scaffold backend modules frontend routing and CI"
git push -u origin chore/project-foundation
```
Open PR: `chore/project-foundation` -> `develop`.

## Notes
- React Router dependency must be added by `npm install` (updates package-lock.json).
- Do not replace the repository's package-lock.json with an outdated copy.
- Backend CI assumes Maven dependencies resolve; when DB/Flyway is enabled, teammate must configure a test profile or test PostgreSQL service to keep `mvn test` green.
- `GET /api/health` is existing endpoint. No authentication has been implemented.
- Feature branches should be created from up-to-date `develop` only when a task begins.

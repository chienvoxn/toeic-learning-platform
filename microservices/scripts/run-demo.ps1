# Run from the microservices folder in PowerShell, with Docker Desktop open.
$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env. EDIT ALL SECRET VALUES, then re-run this script.' -ForegroundColor Yellow
    exit 1
}
docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration' }
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw 'Docker build/start failed. Inspect docker compose logs.' }
docker compose ps
Write-Host 'Open http://localhost:8088. Check logs if services are not ready.' -ForegroundColor Green

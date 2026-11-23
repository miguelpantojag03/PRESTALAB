@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === PrestaLab: levantando los contenedores (API, MongoDB, Mailpit y frontend) ===
if not exist ".env" (
  echo Creando .env con un secreto JWT y una clave de MongoDB nuevos...
  powershell -NoProfile -Command "$b=New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); $s=[Convert]::ToBase64String($b); $p=-join ((48..57)+(97..122) | Get-Random -Count 24 | %% {[char]$_}); Set-Content -Encoding ascii .env ('MONGO_USER=prestalab',('MONGO_PASSWORD='+$p),('PRESTALAB_JWT_SECRET='+$s))"
)
docker info >nul 2>&1
if errorlevel 1 (
  echo Docker no esta corriendo. Abre Docker Desktop, espera a que diga "Engine running" y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)
docker compose up -d --build
if errorlevel 1 (
  echo Fallo docker compose. Revisa el mensaje de arriba.
  pause
  exit /b 1
)
echo Esperando a que la API responda en http://localhost:8080 ...
:esperar
powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing -Method Post -ContentType 'application/json' -Body '{\"correo\":\"admin@prestalab.local\",\"password\":\"Admin123*\"}' http://localhost:8080/api/v1/auth/login | Out-Null; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  timeout /t 3 >nul
  goto esperar
)
echo.
echo === Listo: la API responde. Ya puedes ejecutar la coleccion en Postman. ===
pause

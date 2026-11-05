@echo off
chcp 65001 >nul
REM AleMine -> GitHub hochladen (Doppelklick). GitHub Actions baut danach die .jar.
cd /d "%~dp0"
set REPO_NAME=AleMine
set GH_USER=AleB-Roni

where git >nul 2>nul
if errorlevel 1 (
  echo Git fehlt. Installiere es mit:  winget install Git.Git
  pause
  exit /b 1
)

if not exist ".git" git init -q -b main

git remote get-url origin >nul 2>nul
if errorlevel 1 (
  where gh >nul 2>nul
  if not errorlevel 1 (
    gh auth status >nul 2>nul
    if not errorlevel 1 (
      gh repo create %GH_USER%/%REPO_NAME% --private --source=. --remote=origin >nul 2>nul
    )
  )
)
git remote get-url origin >nul 2>nul
if errorlevel 1 (
  echo Lege auf github.com ein leeres Repo "%REPO_NAME%" an ^(ohne README^) und druecke dann eine Taste.
  pause >nul
  git remote add origin https://github.com/%GH_USER%/%REPO_NAME%.git
)

git pull -q --rebase --autostash origin main >nul 2>nul
git update-index --chmod=+x gradlew >nul 2>nul
git add -A
git update-index --chmod=+x gradlew >nul 2>nul
git commit -q -m "AleMine Update %date% %time%" || echo Keine Aenderungen zum Committen.
git push -u origin main
if errorlevel 1 (
  echo.
  echo Push fehlgeschlagen - Login/Token pruefen.
) else (
  echo.
  echo Hochgeladen. Build laeuft unter:
  echo   https://github.com/%GH_USER%/%REPO_NAME%/actions
  echo Fertige .jar:
  echo   https://github.com/%GH_USER%/%REPO_NAME%/releases/tag/latest
)
pause

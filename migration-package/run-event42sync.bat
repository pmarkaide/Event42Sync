@echo off
REM Event42Sync - Windows Task Scheduler Batch File
REM This script runs the Event42Sync via WSL
REM
REM IMPORTANT: Replace YOUR_WSL_USERNAME with your actual WSL username
REM To find your username, open WSL and run: whoami

wsl -d Ubuntu -u YOUR_WSL_USERNAME -- /home/YOUR_WSL_USERNAME/event42sync/run-sync.sh

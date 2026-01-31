@echo off
REM Event42Sync - Windows Task Scheduler Batch File
REM This script runs the Event42Sync via WSL

wsl -d Ubuntu -u YOUR_WSL_USERNAME -- /home/YOUR_WSL_USERNAME/event42sync/run-sync.sh

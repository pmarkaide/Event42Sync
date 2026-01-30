#!/bin/bash
# Event42Sync - WSL Run Script
# Runs the Event42Sync JAR and logs output

# Navigate to app directory
cd ~/event42sync

# Add timestamp to log
echo "========================================" >> ~/event42sync/sync.log
echo "Sync started at: $(date '+%Y-%m-%d %H:%M:%S')" >> ~/event42sync/sync.log
echo "========================================" >> ~/event42sync/sync.log

# Run the sync
java -jar event42sync.jar >> ~/event42sync/sync.log 2>&1
EXIT_CODE=$?

echo "Sync finished at: $(date '+%Y-%m-%d %H:%M:%S') with exit code: $EXIT_CODE" >> ~/event42sync/sync.log
echo "" >> ~/event42sync/sync.log

exit $EXIT_CODE

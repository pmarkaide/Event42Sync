# Event42Sync Migration to Windows 11 WSL

## Migration Package Contents

| File | Description |
|------|-------------|
| `event42sync.jar` | Main application JAR |
| `.env` | Environment variables (API keys, secrets) |
| `event42sync.json` | Google OAuth credentials |
| `run-sync.sh` | WSL run script |
| `run-event42sync.bat` | Windows batch file for Task Scheduler |

---

## Phase 1: WSL Setup on Windows Server

### 1.1 Install WSL (if not already installed)
Open PowerShell as Administrator:
```powershell
wsl --install -d Ubuntu
```
Restart if prompted, then set up Ubuntu user.

### 1.2 Install Java in WSL
Open WSL terminal:
```bash
sudo apt update
sudo apt install openjdk-17-jre-headless -y
java -version
```
Expected output: `openjdk version "17.x.x"`

### 1.3 Create Application Directory
```bash
mkdir -p ~/event42sync
```

---

## Phase 2: Copy Files to WSL

### 2.1 Copy Migration Package to Windows
Copy the entire `migration-package` folder to the Windows machine (USB, cloud storage, etc.)

### 2.2 Move Files into WSL

**Option A: From Windows Explorer**
Navigate to `\\wsl$\Ubuntu\home\YOUR_USERNAME\event42sync\` and paste the files.

**Option B: From WSL Terminal**
If you placed the files on Windows Desktop:
```bash
# Replace YOUR_WINDOWS_USER with your Windows username
cp /mnt/c/Users/YOUR_WINDOWS_USER/Desktop/migration-package/event42sync.jar ~/event42sync/
cp /mnt/c/Users/YOUR_WINDOWS_USER/Desktop/migration-package/.env ~/event42sync/
cp /mnt/c/Users/YOUR_WINDOWS_USER/Desktop/migration-package/event42sync.json ~/event42sync/
cp /mnt/c/Users/YOUR_WINDOWS_USER/Desktop/migration-package/run-sync.sh ~/event42sync/

# Make script executable
chmod +x ~/event42sync/run-sync.sh
```

### 2.3 Copy Batch File to Windows
Copy `run-event42sync.bat` to `C:\Scripts\`
```powershell
mkdir C:\Scripts
# Then copy the .bat file there
```

### 2.4 Edit Batch File
Open `C:\Scripts\run-event42sync.bat` in Notepad and replace `YOUR_WSL_USERNAME` with your actual WSL username.

To find your WSL username, run in WSL:
```bash
whoami
```

---

## Phase 3: Test Execution

### 3.1 Manual Test in WSL
```bash
cd ~/event42sync
java -jar event42sync.jar
```

Expected output:
```
42 Token Response: {...}
GC Access Token: ...
Starting sync process...
Found X events from 42
...
Sync completed successfully!
```

### 3.2 Test the Run Script
```bash
~/event42sync/run-sync.sh
cat ~/event42sync/sync.log
```

### 3.3 Test from Windows Command Prompt
```cmd
C:\Scripts\run-event42sync.bat
```

Check logs in WSL:
```bash
cat ~/event42sync/sync.log
```

---

## Phase 4: Windows Task Scheduler Setup

### 4.1 Open Task Scheduler
Press `Win + R`, type `taskschd.msc`, press Enter

### 4.2 Create New Task
1. Click **Create Task** (not "Create Basic Task")

2. **General tab**:
   - Name: `Event42Sync Daily`
   - Description: `Syncs 42 events to Google Calendar`
   - Select: "Run whether user is logged on or not"
   - Check: "Run with highest privileges"

3. **Triggers tab**:
   - Click "New..."
   - Begin the task: "On a schedule"
   - Settings: Daily
   - Start: Select tomorrow's date, time: `12:00:00 AM`
   - Recur every: 1 days
   - Click OK

4. **Actions tab**:
   - Click "New..."
   - Action: "Start a program"
   - Program/script: `C:\Scripts\run-event42sync.bat`
   - Start in: `C:\Scripts`
   - Click OK

5. **Conditions tab**:
   - Uncheck "Start only if the computer is on AC power"
   - (Optional) Check "Wake the computer to run this task"

6. **Settings tab**:
   - Check "Allow task to be run on demand"
   - Check "Run task as soon as possible after a scheduled start is missed"
   - Check "If the task fails, restart every: 5 minutes"
   - Attempt to restart up to: 3 times
   - Click OK

7. Enter your Windows password when prompted

### 4.3 Test the Scheduled Task
1. Right-click the task "Event42Sync Daily"
2. Select "Run"
3. Check WSL logs: `cat ~/event42sync/sync.log`

---

## Verification Checklist

### WSL Setup
- [ ] WSL installed with Ubuntu
- [ ] Java 17 installed (`java -version` works)
- [ ] Directory created: `~/event42sync/`
- [ ] Files copied:
  - [ ] `event42sync.jar`
  - [ ] `.env`
  - [ ] `event42sync.json`
  - [ ] `run-sync.sh`
- [ ] Run script is executable (`chmod +x`)

### Windows Setup
- [ ] Batch file at `C:\Scripts\run-event42sync.bat`
- [ ] Batch file edited with correct WSL username

### Testing
- [ ] Manual test in WSL successful
- [ ] Run script test successful
- [ ] Windows batch file test successful
- [ ] Logs appear in `~/event42sync/sync.log`

### Task Scheduler
- [ ] Task created with correct settings
- [ ] On-demand run successful
- [ ] Scheduled run confirmed (wait until midnight or trigger manually)

---

## Troubleshooting

### "java: command not found"
```bash
sudo apt install openjdk-17-jre-headless -y
```

### "Permission denied" on run-sync.sh
```bash
chmod +x ~/event42sync/run-sync.sh
```

### Task runs but nothing happens
Check Windows Event Viewer:
1. Open Event Viewer
2. Navigate to: Windows Logs > Application
3. Look for Task Scheduler errors

Also check the WSL logs:
```bash
cat ~/event42sync/sync.log
```

### WSL not starting
In PowerShell:
```powershell
wsl --list --verbose
wsl --status
```

### Google Calendar authentication error
The `event42sync.json` credentials may need to be refreshed. The app should handle token refresh automatically, but if issues persist, regenerate credentials from Google Cloud Console.

---

## Important Notes

1. **Database Location**: SQLite database is stored at `/tmp/events.db` in WSL. WSL's `/tmp` persists across reboots (unlike real Linux).

2. **Logs Location**:
   - WSL: `~/event42sync/sync.log`
   - Windows: `\\wsl$\Ubuntu\home\USERNAME\event42sync\sync.log`

3. **No Code Changes**: The existing JAR works as-is, no modifications needed.

4. **WSL Auto-Start**: Task Scheduler will automatically start WSL when running the batch file.

---

## AWS Cleanup (After Verification)

Once the Windows setup is working correctly, clean up AWS resources to avoid billing. See `AWS_CLEANUP.md` for detailed commands.

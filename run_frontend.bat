@echo off
setlocal
cd /d %~dp0frontend

if not exist node_modules (
  echo Installing frontend dependencies (first run only)...
  call npm install
)

echo Starting the React dev server. It will print a local URL (usually
echo http://localhost:5173) -- open that in your browser once it's ready.
echo Leave this window open while using the console; close it to stop the server.
call npm run dev
pause

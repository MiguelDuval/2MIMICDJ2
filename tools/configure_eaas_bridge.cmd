@echo off
setlocal

rem 2MIMICDJ2 Windows compatibility bridge using built-in portproxy.
rem Run this file from an Administrator Command Prompt.
rem Usage: configure_eaas_bridge.cmd PHONE_IP

if "%~1"=="" (
  echo Usage: %~nx0 PHONE_IP
  echo Example: %~nx0 10.93.187.7
  exit /b 2
)

set "PHONE_IP=%~1"

echo Configuring Engine-compatible TCP forwarding to %PHONE_IP%...
echo.
echo   PC TCP 50010  ^>  PHONE TCP 50100
echo   PC TCP 50020  ^>  PHONE TCP 50110
echo.

netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=50010 >nul 2>&1
netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=50020 >nul 2>&1

netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=50010 connectaddress=%PHONE_IP% connectport=50100
if errorlevel 1 (
  echo ERROR: failed to create TCP/50010 forwarding.
  exit /b 1
)

netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=50020 connectaddress=%PHONE_IP% connectport=50110
if errorlevel 1 (
  echo ERROR: failed to create TCP/50020 forwarding.
  netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=50010 >nul 2>&1
  exit /b 1
)

netsh advfirewall firewall delete rule name="2MIMICDJ2 EAAS gRPC 50010" >nul 2>&1
netsh advfirewall firewall delete rule name="2MIMICDJ2 EAAS HTTP 50020" >nul 2>&1

netsh advfirewall firewall add rule name="2MIMICDJ2 EAAS gRPC 50010" dir=in action=allow protocol=TCP localport=50010
if errorlevel 1 echo WARNING: firewall rule for TCP/50010 could not be created.

netsh advfirewall firewall add rule name="2MIMICDJ2 EAAS HTTP 50020" dir=in action=allow protocol=TCP localport=50020
if errorlevel 1 echo WARNING: firewall rule for TCP/50020 could not be created.

echo.
echo Active forwarding:
netsh interface portproxy show v4tov4

echo.
echo PC LAN address for the 2MIMICDJ2 "Engine compatibility bridge" field:
ipconfig ^| findstr /R /C:"IPv4 Address" /C:"IPv4-адрес"

echo.
echo Enter one PC LAN IPv4 address above in 2MIMICDJ2, then start the phone server.
echo To remove the forwarding later, run:
echo   netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=50010
echo   netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=50020

endlocal

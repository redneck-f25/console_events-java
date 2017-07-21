@echo off

setlocal enableextensions enabledelayedexpansion

title Hello, I am a Windows Console Batch Skript.
color 2a

qwinsta

timeout /t -1 /nobreak

echo bye

timeout /t 2

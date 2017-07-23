@echo off

pushd %~dp0

javaw -classpath "bin;lib/jna-4.4.0.jar;lib/jna-platform-4.4.0.jar" console_events.ConsoleEvents

popd

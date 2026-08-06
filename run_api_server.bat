@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d %~dp0

rem Runs off Maven's own dependency-resolved classpath (mysql-connector-j included via
rem pom.xml + the exec-maven-plugin) instead of a hand-copied driver jar under lib\.
call mvn -q compile exec:java -Dexec.mainClass=com.branchteller.api.ApiServer
pause

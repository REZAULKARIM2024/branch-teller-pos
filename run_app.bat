@echo off
setlocal
set DRIVER=lib\mysql-connector-j-9.7.0.jar
if not exist target\classes (
  echo Compiling...
  mkdir target\classes 2>nul
  javac -d target\classes -cp %DRIVER% -encoding UTF-8 $(dir /s /b src\main\java\*.java)
)
java -cp "target\classes;%DRIVER%" com.branchteller.Main

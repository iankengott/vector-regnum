@echo off
setlocal

set DIR=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIR:~0,-1%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

"%JAVA_EXE%" %JVM_OPTS% -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

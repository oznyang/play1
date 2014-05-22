@echo off
setlocal ENABLEDELAYEDEXPANSION

echo "~        _            _    "
echo "~  _ __ | | __ _ _  _| |   "
echo "~ | '_ \| |/ _' | || |_|   "
echo "~ |  __/|_|\____|\__ (_)   "
echo "~ |_|            |__/      "
echo "~                          "
echo "                           "

cd /d %~dp0..\
set BASEDIR=%CD%
echo       App path: %BASEDIR%

if "%JAVACMD%"=="" set JAVACMD=java
if not "%JAVA_HOME%"=="" set JAVACMD=%JAVA_HOME%\bin\%JAVACMD%
echo  Using JAVACMD: %JAVACMD%

FOR /R .\lib %%G IN (*.jar) DO set CLASSPATH=!CLASSPATH!;%%G

set JAVA_OPTS=%JAVA_OPTS% -server -Xms128m -Xmx1024m -XX:MaxPermSize=256m

%JAVACMD% %JAVA_OPTS% -XX:-UseSplitVerifier -XX:CompileCommand=exclude,jregex/Pretokenizer,next -Dfile.encoding=utf-8 -Dprecompiled=true -Dapplication.path=%BASEDIR% -classpath %CLASSPATH% play.server.Server %*
pause
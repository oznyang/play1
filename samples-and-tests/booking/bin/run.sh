#!/bin/bash

echo "~        _            _    "
echo "~  _ __ | | __ _ _  _| |   "
echo "~ | '_ \| |/ _' | || |_|   "
echo "~ |  __/|_|\____|\__ (_)   "
echo "~ |_|            |__/      "
echo "~                          "
echo "                           "

cd `dirname $0`/../
BASEDIR=`pwd`
echo "      App path: $BASEDIR"

if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
  else
    JAVACMD=`which java`
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi
echo " Using JAVACMD: $JAVACMD"

CLASSPATH=$(JARS=(lib/*.jar); IFS=:; echo "${JARS[*]}")

JAVA_OPTS="$JAVA_OPTS -server -Xms128m -Xmx1024m -XX:MaxPermSize=256m"

exec "$JAVACMD" $JAVA_OPTS -XX:-UseSplitVerifier -XX:CompileCommand=exclude,jregex/Pretokenizer,next -Dfile.encoding=utf-8 -Dprecompiled=true -Dapplication.path=$BASEDIR -classpath "$CLASSPATH" play.server.Server "$@"


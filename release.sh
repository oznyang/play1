#!/bin/sh

rv=$1
dv=$2

if [ $# -ge 2 ]
then
    mvn release:prepare -Pplugin -Dtag=plugin-${rv} -DreleaseVersion=${rv} -DdevelopmentVersion=${rv}-SNAPSHOT
    git checkout plugin-${rv}
    mvn install -Pplugin
    git clean -dfx

    git checkout scc
    git tag -d plugin-${rv}
    git reset --hard HEAD~2
    mvn install -Pplugin
    mvn release:prepare -Dtag=play-${rv} -DreleaseVersion=${rv} -DdevelopmentVersion=${dv}-SNAPSHOT
    git checkout play-${rv}
    mvn deploy
    git checkout scc
    git clean -dfx
else
    echo "release.sh [releaseVersion] [developmentVersion]"
fi


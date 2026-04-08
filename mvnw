#!/bin/sh
set -e

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/java}"

if [ -z "$JAVA_CMD" ]; then
  JAVA_CMD="java"
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven wrapper..."
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Could not find curl or wget to download Maven wrapper."
    exit 1
  fi
fi

exec "$JAVA_CMD" "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"

#!/usr/bin/env sh
# Maven Wrapper - minimal script
BASEDIR=$(cd "$(dirname "$0")"; pwd -P)
MVNW_REPOURL="https://repo.maven.apache.org/maven2"
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
JAR="$WRAPPER_DIR/maven-wrapper.jar"
PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -s "$JAR" ]; then
  echo "Downloading Maven Wrapper jar..."
  mkdir -p "$WRAPPER_DIR"
  # URL from properties (wrapperUrl) if present, else default
  WRAPPER_URL=$(grep -E '^wrapperUrl=' "$PROPS" 2>/dev/null | cut -d= -f2-)
  if [ -z "$WRAPPER_URL" ]; then
    WRAPPER_URL="$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  fi
  curl -fsSL "$WRAPPER_URL" -o "$JAR" || wget -q "$WRAPPER_URL" -O "$JAR"
fi

JAVA_EXEC=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVA_EXEC" -jar "$JAR" "$@"

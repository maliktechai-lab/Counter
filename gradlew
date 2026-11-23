#!/bin/sh
# Thin wrapper that delegates to a locally-installed Gradle 8.9.
# GRADLE_HOME is auto-detected from the standard install location used during setup.

JAVA_HOME_LOCAL=/home/vanshmalik/tools/jdk-17.0.9+9
GRADLE_HOME_LOCAL=/home/vanshmalik/tools/gradle-8.9

# Prefer the environment-supplied JAVA_HOME if already set to a JDK; otherwise use local.
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
    export JAVA_HOME="$JAVA_HOME_LOCAL"
fi
export PATH="$JAVA_HOME/bin:$PATH"

exec "$GRADLE_HOME_LOCAL/bin/gradle" "$@"

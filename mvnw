#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Maven Start Up Batch script
#
# Required ENV vars:
# JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# M2_HOME - location of maven2's installed home dir
# MAVEN_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
# MAVEN_BATCH_PAUSE - set to 'on' to wait for a keystroke before ending
# MAVEN_OPTS - parameters passed to the Java VM when running Maven
#    Example: set MAVEN_OPTS=-Xms256m -Xmx512m
# MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
mingw=false
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true;;
  Darwin*) darwin=true
    # Use Java Version 11+ on macOS if JAVA_HOME is not set
    if [ -z "$JAVA_HOME" ]; then
      # Try to find Java 11 or newer
      for candidate in \
        "/Library/Java/JavaVirtualMachines/"*"/Contents/Home" \
        "$HOME/Library/Java/JavaVirtualMachines/"*"/Contents/Home" \
        "/usr/local/opt/openjdk"* \
        "/opt/homebrew/opt/openjdk"* ; do
        if [ -d "$candidate" ]; then
          JAVA_HOME="$candidate"
          break
        fi
      done
    fi
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=`java-config --jre-home`
  fi
fi

if [ -z "$JAVA_HOME" ] ; then
  if [ "$darwin" = "true" ] ; then
    # Try to find Java using /usr/libexec/java_home
    JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
    if [ -z "$JAVA_HOME" ]; then
      # Try default location for Homebrew Java
      if [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
        JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
      elif [ -d "/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
        JAVA_HOME="/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
      fi
    fi
  fi
fi

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD=`which java`
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

if [ -z "$JAVA_HOME" ] ; then
  echo "Warning: JAVA_HOME environment variable is not set."
fi

CLASS_PATH=""
WRAPPER_JAR=""
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

MAVEN_CMD_LINE_ARGS="$*"

# Determine the path to the project base dir (the directory containing the .mvn directory)
MAVEN_PROJECTBASEDIR="$PWD"
while [ "$MAVEN_PROJECTBASEDIR" != "/" ] && [ ! -d "$MAVEN_PROJECTBASEDIR/.mvn" ]; do
  MAVEN_PROJECTBASEDIR="$(dirname "$MAVEN_PROJECTBASEDIR")"
done

# Build the classpath from the wrapper jar and any other jars in the .mvn directory
if [ -d "$MAVEN_PROJECTBASEDIR/.mvn" ]; then
  for jar in "$MAVEN_PROJECTBASEDIR/.mvn"/*.jar; do
    if [ -f "$jar" ]; then
      CLASS_PATH="$CLASS_PATH:$jar"
      if [ -z "$WRAPPER_JAR" ]; then
        WRAPPER_JAR="$jar"
      fi
    fi
  done
fi

# If wrapper jar not found, download it
if [ -z "$WRAPPER_JAR" ]; then
  # Use the default wrapper URL if not set
  if [ -z "$MVNW_REPOURL" ]; then
    MVNW_REPOURL="https://repo.maven.apache.org/maven2"
  fi
  WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
  WRAPPER_URL="$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

  if [ ! -f "$WRAPPER_JAR" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -s -o "$WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
      echo "Error: Could not download maven-wrapper.jar. Please install curl or wget."
      exit 1
    fi
  fi
  CLASS_PATH="$WRAPPER_JAR:$CLASS_PATH"
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --path --mixed "$JAVA_HOME"`
  [ -n "$MAVEN_PROJECTBASEDIR" ] && MAVEN_PROJECTBASEDIR=`cygpath --path --mixed "$MAVEN_PROJECTBASEDIR"`
  [ -n "$CLASS_PATH" ] && CLASS_PATH=`cygpath --path --mixed "$CLASS_PATH"`
fi

# Set default Maven goals if none provided
if [ -z "$MAVEN_CMD_LINE_ARGS" ]; then
  MAVEN_CMD_LINE_ARGS="compile"
fi

# Execute Maven
exec "$JAVACMD" \
  $MAVEN_OPTS \
  -classpath "$CLASS_PATH" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  $WRAPPER_LAUNCHER \
  $MAVEN_CMD_LINE_ARGS
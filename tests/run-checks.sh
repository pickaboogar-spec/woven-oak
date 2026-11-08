#!/bin/bash
# Run the Woven Oak voice check suite on the JVM.
# Toolchain paths default to Pepper's build machine; override
# JAVA_HOME / KOTLINC_HOME if yours live elsewhere.
set -e
export JAVA_HOME=${JAVA_HOME:-/home/hatch/workspace/jdk}
export PATH=$JAVA_HOME/bin:$PATH
KOTLINC=${KOTLINC_HOME:-/home/hatch/workspace/kotlin-compiler/kotlinc}/bin/kotlinc
STDLIB=${KOTLINC_HOME:-/home/hatch/workspace/kotlin-compiler/kotlinc}/lib/kotlin-stdlib.jar
SRC=$(dirname "$0")/../android/app/src/main/java/com/damonini/wovenoak
WORK=$(mktemp -d)
mkdir -p $WORK/stub/android/content $WORK/classes
cat > $WORK/stub/android/content/Context.java <<'JAVA'
package android.content;
import java.io.File;
public class Context {
    private final File filesDir;
    public Context(File f) { this.filesDir = f; }
    public File getFilesDir() { return filesDir; }
}
JAVA
javac -d $WORK/classes $WORK/stub/android/content/Context.java
$KOTLINC -cp $WORK/classes -d $WORK/checks.jar \
  $SRC/Pitch.kt $SRC/Calibrate.kt $SRC/VoiceBank.kt $SRC/VoicePlay.kt \
  $(dirname "$0")/VoiceChecks.kt
java -cp $WORK/checks.jar:$WORK/classes:$STDLIB VoiceChecksKt

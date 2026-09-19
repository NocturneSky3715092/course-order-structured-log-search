#!/bin/sh
set -eu

classes="${TMPDIR:-/tmp}/course-order-log-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" education.store.logs.CourseOrderJob "${1:-order-course-1042}"

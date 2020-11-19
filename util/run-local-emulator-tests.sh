#!/bin/bash
# Description:
#  Runs the emulator tests locally
#
#  To run this script, just pass in a valid avd name:
#
#    $ path/to/run-local-emulator-tests.sh Android29

set -ex

readonly EMULATOR="$1"
# Empirically, the emulator startup takes ~60s so we set the timeout for 200s.
readonly TIMEOUT_SEC=200
readonly WAIT_CMD="${ANDROID_HOME}/platform-tools/adb wait-for-device shell getprop init.svc.bootanim"

# Start the emulator in the background and grab its PID so we can kill it later.
${ANDROID_HOME}/emulator/emulator -avd $EMULATOR -no-window -wipe-data &
EMULATOR_PID=$!

# Check that the emulator actually started. If not, exit the script.
if ps -p $EMULATOR_PID > /dev/null
then
  echo "Created emulator."
else
  echo "Failed, to create emulator."
  exit 1
fi

# Cleanup the emulator on exit, even if something fails.
function cleanup {
  exit_status=$?
  echo "Cleaning up emulator."
  # Kill the process the emulator is running on
  kill -9 $EMULATOR_PID
  exit $exit_status
}
trap cleanup EXIT

# Wait for the emulator to fully boot up, or for the timeout to expire.
RETRY_COUNT=0
until $WAIT_CMD | grep -m 1 stopped; do
  echo "Waiting for emulator..."
  if [ $RETRY_COUNT -eq $TIMEOUT_SEC ];
    then { echo "Failed, to load emulator." ; exit 1; }
    else { echo "Emulator status: $($WAIT_CMD)"; }
  fi
  RETRY_COUNT=$((RETRY_COUNT+1))
  sleep 1
done

readonly GRADLE_PROJECTS=(
    "javatests/artifacts/hilt-android/simple"
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project"
    ./$project/gradlew -p $project connectedAndroidTest --no-daemon --stacktrace
done

#!/bin/bash

LUMO="lumo -K -m screencap.core"
PGID_FILE="process_group.pid"

cleanup() {
  echo "Cleaning up..."
  ($LUMO encode-once) | sed 's/^/[Run3] /'
  rm -f "$PGID_FILE"
  echo "Scripts stopped and process group terminated."
}

run_scripts() {
  ($LUMO screenshot | sed 's/^/[Run1] /') &
  ($LUMO encode | sed 's/^/[Run2] /') &
  wait
}

start_scripts() {
  # Ensure cleanup runs on script exit
  trap cleanup EXIT

  # Start a new process group using a subshell
  (
    # Save the PGID (which is the same as $$ in this subshell)
    echo $$ > "$PGID_FILE"
    # Set up a trap in the subshell to propagate the signal to the main script
    trap 'kill -INT $$' INT TERM
    echo "Scripts started. Process group ID: $$"
    run_scripts
  ) &
  subshell_pid=$!

  # Set up a trap for the main script
  trap 'kill -TERM $subshell_pid 2>/dev/null; wait $subshell_pid 2>/dev/null; exit 0' INT TERM

  # Wait for the subshell
  wait $subshell_pid
}

case "$1" in
  start)
    start_scripts
    ;;
  *)
    echo "Usage: $0 start"
    exit 1
    ;;
esac
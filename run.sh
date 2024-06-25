#!/bin/sh

LUMO="lumo -K -m screencap.core"
PID1=run1.pid
PID2=run2.pid

stop_scripts() {
  kill `cat $PID1 $PID2 2>/dev/null` 2>/dev/null || true
  rm -f $PID1 $PID2
  ($LUMO encode-once) | sed 's/^/[Run3] /'
  echo "Scripts stopped and PID files removed."
}

case "$1" in
  start)
    trap 'stop_scripts; exit 1' INT TERM EXIT
    ($LUMO screenshot & echo $! > $PID1) | sed 's/^/[Run1] /' &
    ($LUMO encode & echo $! > $PID2) | sed 's/^/[Run2] /' &
    wait
    stop_scripts
    ;;
  stop)
    stop_scripts
    ;;
  *)
    echo "Usage: $0 {start|stop}"
    exit 1
    ;;
esac

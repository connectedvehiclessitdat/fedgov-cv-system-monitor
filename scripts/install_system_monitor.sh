#!/bin/bash

if [[ $(/usr/bin/id -u) -ne 0 ]]; then
    echo "Must be run as root"
    exit
fi

# First modify all files containing %PATH_TO_CV-SYSTEM-MONITOR-DIR%, except this script,
# to contain the correct path for where the system monitor is being installed(the directory
# of the install script) 
 
# Assuming this script is in the cv-system-monitor directory,
# get the directory of cv-system-monitor
SYSTEM_MONITOR_DIR=$( cd $(dirname $0) ; pwd -P )
SCRIPT_NAME=$( basename $0 )

grep -rl '%PATH_TO_CV-SYSTEM-MONITOR-DIR%' "$SYSTEM_MONITOR_DIR" | grep -v "$SCRIPT_NAME" | xargs sed -i "s~%PATH_TO_CV-SYSTEM-MONITOR-DIR%~$SYSTEM_MONITOR_DIR~g"

# Make sure that all the scripts are executable
cd "$SYSTEM_MONITOR_DIR"
chmod +x *.sh
fromdos *.sh

# Next, setup to run via upstart
INIT_DIR=/etc/init

if [ ! -d $INIT_DIR ]; then
  echo "Directory /etc/init doesn't exist"
  exit 1
fi

# Create the cv-system-monitor upstart job

if [ -f $INIT_DIR/cv-system-monitor.conf ]; then
  rm -f $INIT_DIR/cv-system-monitor.conf
fi

touch $INIT_DIR/cv-system-monitor.conf

cat >> $INIT_DIR/cv-system-monitor.conf << EOF
#
# This conf defines the cv-system-monitor upstart command. It is used
# to start the cv-system-monitor and respawn it when it terminates unexpectedly.
#

description     "cv system monitor"

respawn

setuid root

exec $SYSTEM_MONITOR_DIR/start_system_monitor.sh $SYSTEM_MONITOR_DIR/config/system_monitor_config.json
EOF

start cv-system-monitor
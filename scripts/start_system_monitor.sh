#!/bin/bash

java -Dlog4j.configuration=file:config/log4j.properties -jar fedgov-cv-system-monitor-1.0.0-SNAPSHOT-jar-with-dependencies.jar -c $1
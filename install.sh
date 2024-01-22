#! /bin/sh

## This startup script runs ON the remote machine
JAR_NAME=drone-1.0-SNAPSHOT.jar
pkill -f 'java -jar /home/pi/helloworld/${JAR_NAME}'
nohup java -jar ~/helloworld/${JAR_NAME} > ~/helloworld/$(date -d "today" +"%Y%m%d%H%M").log
exit
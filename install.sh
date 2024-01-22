#! /bin/sh

## This startup script runs ON the remote machine
JAR_NAME=myJar.jarpkill -f 'java -jar /home/pi/helloworld/myJar.jar'
nohup java -jar ~/helloworld/${JAR_NAME} > ~/helloworld/$(date -d "today" +"%Y%m%d%H%M").log
exit
#! /bin/sh

./gradlew clean build

JAR_NAME=myJar.jar

ssh drone@openhd 'mkdir -p ~/helloworld'

scp -r ./build/libs/${JAR_NAME} drone@openhd:~/helloworld/

ssh drone@openhd 'bash -s' < ./install.sh
exit
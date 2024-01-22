./gradlew clean build

JAR_NAME=drone-1.0-SNAPSHOT.jar
ssh drone@openhd 'mkdir -p ~/helloworld'

scp -r ./build/libs/${JAR_NAME} drone@openhd:~/helloworld/

cat install.sh | ssh drone@openhd 'bash -s'
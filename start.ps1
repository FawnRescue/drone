# Equivalent of './gradlew clean build'
& "./gradlew" clean build

# Setting the JAR_NAME variable
$JAR_NAME = "drone-1.0-SNAPSHOT.jar"

# SSH command to create a directory on the remote server
ssh drone@openhd 'mkdir -p ~/helloworld'

# SCP command to copy the JAR file to the remote server
scp -r "./build/libs/$JAR_NAME" drone@openhd:~/helloworld/

# Execute the JAR file on the remote server
ssh drone@openhd "java -jar ~/helloworld/$JAR_NAME"

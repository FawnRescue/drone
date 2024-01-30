# Equivalent of './gradlew clean build'
& "./gradlew" clean build

# Setting the JAR_NAME variable
$JAR_NAME = "drone-1.0-SNAPSHOT.jar"

# SCP command to copy the JAR file to the remote server
scp -r "./build/libs/$JAR_NAME" drone@openhd:~/

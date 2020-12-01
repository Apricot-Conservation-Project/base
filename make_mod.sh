echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/testing/config/mods

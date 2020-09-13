echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/game/server/assimilation-server/config/mods


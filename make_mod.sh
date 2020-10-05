echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/game/server/assimilation-server/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/game/server/hub-server/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/game/server/campaign-server/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/game/server/plague1-server/config/mods


echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/assim/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/plague1/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/hub/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/campaign/config/mods
cp ./build/libs/AABasePlugin.jar ~/Documents/mindustry/V6/servers/assault/config/mods

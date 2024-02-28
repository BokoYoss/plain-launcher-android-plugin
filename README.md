# Plain Launcher Android plugin
This repo contains code to support the Plain Launcher godot application.

Used by https://github.com/BokoYoss/plain-launcher, but feel free to take stuff from this for your own projects if you are making a Godot app that launches intents.

### Building the Android plugin
- Configure the path to your Android sdk by putting `sdk.dir=<path_to_sdk>` in local.properties
- In a terminal window, navigate to the project's root directory and run the following command:
```
./gradlew assemble
```
- On successful completion of the build, the output files can be found in
  `plugin/build/output/addons`


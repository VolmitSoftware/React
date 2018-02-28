# React 6
React 6.0 is a full rewrite and it has been worked on for the past 2 months. Here is a checklist, and a few other target goals to keep track of it's progress.
  
## Required Testing
Testing is pretty much needed since this update wont be going to only a few people. All react owners (oh yeah and all of the pirates out there sailing the seas of react) will be getting this. So it's crucial to ensure it works on 6.0.0, and not 6.2.5 for example.

### Version & Protocol Testing
React may use packets or tricks to accomplish something, it needs to be tested.

| Version | Actionbar & Title | Event Handling | Hotloading & Reloading | COFOBF |
|---|---|---|---|---|
| 1.12.X | Pass | Pass | Pass | **Fail** |
| 1.11.X | Pass | Pass | Pass | **Fail** |
| 1.10.X | Pass | Pass | Pass | **Fail** |
| 1.9.X | Pass | Pass | Pass | **Fail** |
| 1.8.X | Pass | Pass | Pass | **Fail** |
| 1.7.10 | **Fail** | Pass | Pass | **Fail** |

## FAQ
Some random questions people have asked in the discord development channel for react6

### Is this a new plugin? Where will it be posted
Nope, it will be an update for react :P. If you own react, you will get 6.0 on spigot just like any other update.

### How will the new configurations translate to my current configs
Still debating on this. It is clear that react will have a much simpler configuration (think wormholes config.yml & config-experimental.yml), however this obviously adds to the conversion of configurations. For example some features may not be included (or may be different) in react6. It will most likley contain a converter with a notification in the console (possibly create a backup folder in the event something went wrong?)

### Are you still going to support 1.7?
We will still run FULL support for 1.8 and up. 1.7 is a different story. A lot of the new features in r6 may be disabled if you use it on 1.7. We aren't going to block 1.7 usage, (modpacksftw), but react may not have all features working (they will be turned off cleanly)

### Why is my question not here?
It's 30 degrees in this room, my fingers are frosted over.

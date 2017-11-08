# React 6
React 6.0 is a full rewrite and it has been worked on for the past 2 months. Here is a checklist, and a few other target goals to keep track of it's progress.

## Planned Features
* [x] A Fully fledged thread pool system which allows jumping between the main thread and the pool
* [x] Async sampling
  * [x] Async TPS calculation & averaging
  * [x] Async Tick Time calculation (without using timings)
  * [x] Async Memory profiling (mahs, usage, free, max, mbs, allocations, gcs, and gca)
  * [x] Async World profiling (chunks, entities, worlds, players, counts) (READ ONLY)
* [x] Multicore Monitoring (FULL)
  * [x] Monitor with UTF8 symbols and dingbats (to simplify and shorten the size of text)
  * [x] Double-Crouch to lock/unlock the monitor (easier than the old /re mon -lock) (note: cannot be changed while server is frozen as packets are not processed to the main thread until the next tick finishes)
  * [x] Anti-Creative Scrolling (holding shift to fly down while scrolling to change a block while building shouldnt trip out the monitor)
  * [x] Tick Spike Counting to count "up" the time the server is frozen (since the monitor is async this is now possible)
  * [x] Entity Monitoring tab split between drops, living entities, and tile entities (summed in the tab header)
* [x] Multicore Action System
  * [x] All actions are fired and processed on multiple threads (in the pool) hopping into the main thread to modify the world, entities, chunks etc and back)
  * [x] An advanced selector system to specify WHERE and HOW an action should work with dedicated parsers for each "action selector type". For example a "positional action" can accept which chunks to execute on, and which ones not to.
  * [x] An action queue system which prevents the same action from firing on multiple threads (or more than one at a time)
* [x] React Glass (async physics visualization)
  * [x] Process physics updates into a queue from redstone, blocks, pistons, and other worldly things (if it moves or changes, it is recorded)
  * [x] Categorize each type into a color to show up as glass (stained glass)
  * [x] Off of the main thread, quickly send a packet to change the updated block to colored glass, then back after 70 milliseconds or so
  
## Required Testing
Testing is pretty much needed since this update wont be going to only a few people. All react owners (oh yeah and all of the pirates out there sailing the seas of react) will be getting this. So it's crucial to ensure it works on 6.0.0, and not 6.2.5 for example.

### Version & Protocol Testing
React may use packets or tricks to accomplish something, it needs to be tested.

| Version | Actionbar & Title | Event Handling | Hotloading & Reloading | COFOBF |
|---|---|---|---|---|
| 1.12.X | **Pass** | **Pass** | **Pass** | **Pass** |
| 1.11.X | **TBT** | **TBT** | **TBT** | **TBT** |
| 1.10.X | **TBT** | **TBT** | **TBT** | **TBT** |
| 1.9.X | **TBT** | **TBT** | **TBT** | **TBT** |
| 1.8.X | **TBT** | **TBT** | **TBT** | **TBT** |
| 1.7.10 | **TBT** | **TBT** | **TBT** | **TBT** |

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

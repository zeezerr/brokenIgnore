# World Events

Random hourly server-wide events for Paper/Spigot SMP servers (Minecraft 1.21.x, Java 21).

* Build: push this repo to GitHub, open the **Actions** tab, open the latest "Build plugin" run and download the **WorldEvents-jar** artifact.
* Install: unzip it, drop `WorldEvents.jar` into your server's `plugins/` folder, restart.
* Config: `plugins/WorldEvents/config.yml` (weights, durations, rewards, messages). `/worldevents reload` applies changes.
* Commands: `/worldevents help` (starting/stopping events needs `worldevents.admin`, which only ops have by default)
* Different server version? Edit `<paper.version>` in `pom.xml` (e.g. `1.21.8-R0.1-SNAPSHOT`).
* Add an event: write a class extending `WorldEvent`, then add it in `EventRegistry.java`.

## Admin cheat sheet
* `/worldevents gui` - menu with every event. Click one to open its duration menu (slider + +/- buttons, then Start); right-click a running event to stop it. Bottom row: stop all, start automatic events, random event, extra-event toggle, page arrows.
* `/worldevents random` - spin the wheel for a random event.
* `/worldevents extra` - toggles extra mode: the next event you start runs *alongside* the current one instead of replacing it (max 2 at once; events that clash, e.g. Eternal Day + Eternal Night, can't be combined).
* `/worldevents stop` - stops every event. `/worldevents stop <event>` stops only that one.
* Custom length: `/worldevents random 30m`, `/worldevents start jackpot 2h`. Accepts `30` (minutes), `45m`, `2h`, `90s`, `1h30m` (10 seconds to 24 hours). Without a time, events last `default-duration-minutes` (60).

# React
React is a premium plugin developed by cyberpwn to help keep servers stable while under heavy load, and help admins and/or developers understand what is going on under the hood while ingame quickly. React is different than many other "anti lag" plugins because it is conservative. For example, why would you remove player drops from the world if the server is not lagging, nor is it running low on memory? This is the basis of react. If something is wrong, react to it, otherwise, dont interrupt the player experience.
[View The Wiki](https://github.com/cyberpwnn/React/wiki)

## Samplers
For react to understand what is going on under the hood, it must sample several things further than just the TPS. For example, if the server is lagging, what is causing it? To figure this out, react checks all of its samplers and tries to use judgement of what would be the quickest, and best course of action.
[More on Samplers](https://github.com/cyberpwnn/React/wiki/Samplers)

## Reactions
Once react understands the problem, it will use "reactions" to fix the problem automatically. For example, if the server is lagging because there is a lot of redstone activity (more than normal), then react will try to freeze the redstone. Many things, including mobs, entities, drops, liquids, redstone, and much more are detected and fixed automatically.
[More on Reactions](https://github.com/cyberpwnn/React/wiki/Reactions)

## Monitoring
React is open, meaning you can see in the eyes of react. You can see everything react is monitoring with a beautiful, yet functional hud. This is acheived by using the action bar and title messages. You can view the TPS, memory, chunks, and much more. 
[More on Monitoring](https://github.com/cyberpwnn/React/wiki/Monitoring)

## Configurable
Every Sampler, and every Reaction is highly configurable, and can even be disabled. This makes react a great fit for any server. For example, if you expect the server to lag from time to time with redstone, but do not want it to freeze the redstone, you can esily disable it. React will still notify you about the lag cause, however it will not do anything to fix it if you disable it. 
[More on Configuration](https://github.com/cyberpwnn/React/wiki)

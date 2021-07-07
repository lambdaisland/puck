# puck demo

This demo uses shadow-cljs to setup puck, and in-turn pixi.js dependencies.

We are also using the clojure cli & dep tools `clj` instead of using `lein`.

To run this demo, first ensure you have `clj` [installed](https://clojure.org/guides/getting_started).

Then do an npm install once

```
npm install
```

Now depending on your workflow you can get this up and running in the following ways:

## Emacs + CIDER

If you use emacs + cider, simply open any clj[s] file in the demo folder and run `cider-jack-in-cljs`. This will start up shadow-cljs in watch mode with auto reload.

Visit http://localhost:8008

## Terminal

You can also get the build running directly from the terminal like this:

```
clj -A:dev:test
```

Then in the repl type `(cljs-repl)`

```
user=> (cljs-repl)
shadow-cljs - HTTP server available at http://localhost:8008
shadow-cljs - server version: 2.11.26 running at http://localhost:9630
shadow-cljs - nREPL server started on port 44123
[:main] Configuring build.
[:main] Compiling ...
[:main] Build completed. (186 files, 15 compiled, 0 warnings, 5.77s)
```

Everytime you save a file, it will recompile itself and if possible hot-reload the code.

## Changing entry scripts

There are 3 different demo scripts. Currently only one is loaded at a time.

To change that you can comment/uncomment the scripts inside `shadow-cljs.edn` file
example:

```
...
   :modules    {:main {:entries [#_puck-demo.pacman
                                 puck-demo.circle-packing
                                 #_puck-demo.pixel-editor]}}
...
```

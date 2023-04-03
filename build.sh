#! /bin/sh

# clj -A:cljs-repl build.clj

# clojure -M:cljs-repl build.clj

clojure -M -m figwheel.main --build newsfeed --repl

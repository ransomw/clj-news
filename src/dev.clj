(ns dev
  (:require [clojure.pprint :refer [pprint]]
            [clojure.repl :refer [doc]]
            [clojure.tools.namespace.repl
             :refer [set-refresh-dirs refresh refresh-all]]
            [reloaded.repl :refer [system init]]
            [clj-http.client :as http-c]
            [cheshire.core :as jcat]
            [core :as c]
            [application :as app]
            [cljs.build.api :as cljs-build]))

(defn dev-system
  []
  (app/app-system {}))

(set-refresh-dirs "src")

(def stop reloaded.repl/stop)

(defn go
  []
  (reloaded.repl/set-init! #(dev-system))
  (reloaded.repl/go))

(defn cljs-build-once
  []
  (cljs-build/build "src-ui"
                    {:main 'newsfeed.core
                     :asset-path
                     "js/compiled/out"
                     :output-to
                     "resources/public/js/compiled/newsfeed.js"
                     :output-dir
                     "resources/public/js/compiled/out"
                     :source-map-timestamp true}))

(comment

  (cljs-build-once)

  (c/update-stor
   (:stor reloaded.repl/system))

  (do (stop)
      (go))

  (dev-system)

  (pprint
   (->> (c/get-hn-frontpage)
        (filter #(> (:score %) 100))))

  )

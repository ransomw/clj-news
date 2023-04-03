(ns dev
  (:require [clojure.pprint :refer [pprint]]
            [clojure.repl :refer [doc]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.set]
            [clojure.spec.alpha :as spec]
            [clojure.tools.namespace.repl :refer
             [set-refresh-dirs
              refresh
              refresh-all]]
            [clojure.core.async :as a]
            [reloaded.repl :refer [system init]]
            [clj-http.client :as http-c]
            [cheshire.core :as jcat]
            [com.stuartsierra.component
             :as component]
            [core :as c]
            [tick.alpha.api :as tick-a]
            [application :as app]
            [cljs.build.api :as cljs-build]
            [hickory.core :as puccih]
            [hickory.select :as hsel]
            ))

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

  (:stars
   (second
    @(:!gh (:stor reloaded.repl/system))))

  (dev-system)

  (pprint
   (->> (c/get-hn-frontpage)
        (filter #(> (:score %) 100))))

  )

(comment

  (remove #(comp nil? :link %) @(:!hn (:stor reloaded.repl/system)))

  )

(comment

  @(:!geo-weather (:stor reloaded.repl/system))

  :city-name
  :forecast [:temp :date-time]



  (let [locs
          @(:!geo-weather (:stor reloaded.repl/system))
        hours-diff
        (fn [a b]
          (let [md (comp tick-a/hours
                         tick-a/duration
                         tick-a/new-interval )]
            (cond
              (= a b) 0
              (tick-a/< a b) (md a b)
              :else
              (* -1 (md b a)))))
        ref-date-time
        (-> locs first :forecast
                       first :date-time)]
    (reduce
     (fn [acc {:keys [city-name forecast]}]
       (->>
        forecast
        (map (fn [{:keys [date-time temp]}]
               {:city city-name
                :hours
                (hours-diff
                 ref-date-time date-time)
                :temp temp
                }))
        (into acc)))
     [] locs))

  (def ^:dynamic *before* (tick-a/now))

  (tick-a/minutes
   (tick-a/duration
    (tick-a/new-interval *before* (tick-a/now))))

  ( minutes-diff (tick-a/now) *before*)

  (defn )

  (def ^:dynamic *r*
    (let [[lati longi] ["37.429167" "-122.138056"]
          appid
          "0b2c9c8ac23b3a7595992042a07cd1be"
          r (-> (str
                 "http://api.openweathermap.org/data/2.5/"
                 "forecast?lat=" lati "&lon=" longi
                 "&appid=" appid)
                http-c/get :body jcat/parse-string)]
      r))

  (let [dt-txt
        (get (first (get *r* "list")) "dt_txt")]
    (-> dt-txt
        (string/replace " " "T")
        tick-a/date-time))

  (->> (get *r* "list")
       (map (fn [item]
              (assoc item :date-time
                     (let [dt-txt
                           (get item "dt_txt")]
                       (-> dt-txt
                           (string/replace " " "T")
                           tick-a/date-time))))
            )
       (sort (fn [a b]
               (let [[dt-a dt-b] (map :date-time
                                      [a b])]
                 (tick-a/< dt-a dt-b)
                 )))
       (map (juxt #(get-in % ["main" "temp"])
                  #(get-in % ["weather" 0 "main"])
                  :date-time))
       (map #(zipmap [:temperature
                      :weather
                      :date-time] %)))


  )

(defn ns-names-from-ns-map
  [ns-sym]
  (->> (filter
        (fn [[_ el-var]]
          (let [mns (-> el-var meta :ns)]
            (if (nil? mns)
              false
              (= (ns-name mns) ns-sym)
              ))
          )
        (ns-map ns-sym))
       (map first)))

(comment

  (pprint
   (set (ns-names-from-ns-map 'tick.alpha.api))
   )

  )




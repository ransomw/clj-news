(require '[figwheel-sidecar.repl-api :as repl-api
           :refer [cljs-repl]])

(def port nil)

(repl-api/start-figwheel!
 {:figwheel-options
  (if port
    {:nrepl-port       (some-> port Long/parseLong)
     :nrepl-middleware ["cider.nrepl/cider-middleware"
                        "refactor-nrepl.middleware/wrap-refactor"
                        "cemerick.piggieback/wrap-cljs-repl"]}
    {})
  :all-builds [{:id           "dev"
                :figwheel     true
                :source-paths ["src-ui"]
                :compiler (merge
                           {:main 'newsfeed.core
                            :asset-path
                            "js/compiled/out"
                            :output-to
                            "resources/public/js/compiled/newsfeed.js"
                            :output-dir
                            "resources/public/js/compiled/out"
                            :source-map-timestamp true}
                           {:optimizations :none
                            :source-map    true})}]})
(when-not port
  (cljs-repl))

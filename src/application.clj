(ns application
  (:require [com.stuartsierra.component :as component]
            [system.components
             [endpoint :refer [new-endpoint]]
             [middleware :refer [new-middleware]]
             [handler :refer [new-handler]]
             [http-kit :refer [new-web-server]]]
            [ring.middleware.defaults :refer [wrap-defaults
                                              api-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [routes :refer [routes-main]]
            [core :refer [new-stor]]))

(defn app-system
  [{:keys [http-port] :or {http-port 5001}}]
  (component/system-map
   :stor (new-stor)
   :routes (-> (new-endpoint routes-main)
               (component/using [:stor]))
   :middleware (new-middleware
                {:middleware [[wrap-defaults api-defaults]
                              [wrap-restful-format :format [:edn]]
                              wrap-gzip]})
   :handler (-> (new-handler)
                (component/using [:routes :middleware]))
   :http (-> (new-web-server http-port)
             (component/using [:handler]))))

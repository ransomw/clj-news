(ns routes
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE
                                    routes context]]
            [compojure.route :refer [resources]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.util.response :refer [resource-response]]
            [core :as c]))

;;; utils

(defn load-static-asset-global
  [path]
  (io/input-stream (io/resource path)))

(defn make-edn-resp
  ([data] (make-edn-resp data 200))
  ([data status]
   {:status status
    :headers {"Content-Type" "application/edn"}
    :body (pr-str data)}))

;;; routes

(defn api-routes
  [stor]
  (routes
   (GET "/news" _
        (do
          (c/update-stor stor)
          (-> {:hn @(:!hn stor)
               :twitter @(:!tw stor)
               :gh @(:!gh stor)
               }
              make-edn-resp)))))

(defn routes-main
  [{:keys [stor] :as endpoint}]
  (routes
   (GET "/" _
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (load-static-asset-global "public/index.html")})
   (GET "/hello" _
        "hello world..")
   (context
    "/api" _
    (-> (api-routes stor)
        (wrap-restful-format :format [:edn])))
   (resources "/")))

(comment


  (resource-response (str "public" "/" "js/compiled/newsfeed.js"))

  )

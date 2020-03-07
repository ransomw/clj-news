(ns routes
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [tick.alpha.api :as tick-a]
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

(defn hn-within-duration?
  [hn-item]
  (let [most-recent (tick-a/new-duration 12 :hours)]
    (letfn
        [(duration-type->re
           [duration-type]
           (re-pattern (str "(.*)" (duration-type
                                    {:hours "hours"})
                            ".*")))
         (find-duration-by-type
           [hn-item-age duration-type]
           (if (duration-type {:hours "hours"})
             (some-> ((comp second re-find)
                      (re-matcher
                       ;; map tick kw's to hn text
                       (duration-type->re duration-type)
                       hn-item-age))
                     string/trim
                     Integer.
                     (tick-a/new-duration
                      (keyword duration-type)))))]
      (let [hn-item-age (:age hn-item)
            fdft (partial find-duration-by-type
                          hn-item-age)
            standard-duration
            (or (reduce #(or %1 %2)
                        (map fdft [:hours]))
                most-recent)]
        (tick-a/< standard-duration most-recent)))))

(defn api-routes
  [stor]
  (routes
   (GET "/news" _
        (do
          (c/update-stor stor)
          (-> {:hn
               (let [hn @(:!hn stor)]
                 (->> hn
                      (filter hn-within-duration?)
                      (sort (fn [a b] (> (:score a) (:score b))))
                      (take 15)))
               :twitter @(:!tw stor)
               :gh @(:!gh stor)
               :geo-weather @(:!geo-weather stor)
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

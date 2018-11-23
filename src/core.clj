(ns core
  (:import java.util.Base64)
  (:require [clojure.edn :as edn]
            [clj-http.client :as http-c]
            [cheshire.core :as jcat]
            [hickory.core :as puccih]
            [hickory.select :as hsel]
            [com.stuartsierra.component :as component]))

(defonce bearer-token nil)

(defn encode-base64
  [to-encode]
  (String. (.encode (Base64/getEncoder) (.getBytes to-encode))))

(defn get-twit-bearer-token
  [_]
  (let [{token :api-key
         token-secret :api-secret-key} (-> "resources/creds.edn"
                                           slurp edn/read-string)
        auth-header (str "Basic " (-> (str token ":" token-secret)
                                      encode-base64))
        http-res (http-c/post
                  (str "https://api.twitter.com"
                       "/oauth2/token")
                  {:headers {"Authorization" auth-header}
                   :form-params {:grant_type "client_credentials"}})
        _ (when (not= 200 (:status http-res))
            (throw (ex-info "not-ok status on bearer token response"
                            http-res)))
        res-body (jcat/parse-string (:body http-res))
        _ (when (not= "bearer" (get res-body "token_type"))
            (throw (ex-info "not a bearer token" res-body)))]
    (get res-body "access_token")))

(defn set-twit-bearer-token
  []
  (alter-var-root #'bearer-token get-twit-bearer-token))

(defn get-twts-to
  [username]
  (when (nil? bearer-token)
    (set-twit-bearer-token))
  (let [twts-resp (->
                   (str
                    "https://api.twitter.com"
                    "/1.1/search/tweets.json?q=%40" username)
                   (http-c/get
                    {:headers {"Authorization"
                               (str "Bearer " bearer-token)}})
                   :body jcat/parse-string
                   (get "statuses"))]
    (letfn [(format-twt [twt]
              {:text (get twt "text")
               :username (get-in twt ["user" "screen_name"])})]
      (map format-twt twts-resp))))

(defn get-twts-from
  [username]
  (when (nil? bearer-token)
    (set-twit-bearer-token))
  (let [twts-resp (->
                   (str
                    "https://api.twitter.com"
                    "/1.1/statuses/user_timeline.json?"
                    "screen_name=" username
                    "&count=" 3)
                   (http-c/get
                    {:headers {"Authorization"
                               (str "Bearer " bearer-token)}})
                   :body jcat/parse-string)]
    (letfn [(format-twt [twt]
              {:text (get twt "text")
               :time (get twt "created_at")})]
      (map format-twt twts-resp))))

(defn get-hn-frontpage
  []
  (let [fpp (-> "http://news.ycombinator.com"
                (http-c/get)
                :body puccih/parse)
        ;; these are the links and titles
        lt (->> fpp
                puccih/as-hickory
                (hsel/select (hsel/child (hsel/class "athing")))
                (map
                 #(hsel/select (hsel/child (hsel/class "storylink")) %))
                (map first)
                (map #((juxt (comp :href :attrs)
                             (comp first :content)) %))
                (map #(zipmap [:link :text] %)))
        ;; these are the points and comments
        pc (letfn [(select-score [pnc]
                     (or
                      (some->> pnc
                               (hsel/select
                                (hsel/child (hsel/class "score")))
                               first :content first
                               (re-find #"\d+") Integer/parseInt)
                      0))
                   (select-age [pnc]
                     (->> pnc
                          (hsel/select (hsel/child (hsel/class "age")
                                                   hsel/first-child))
                          first :content first))
                   (select-comments [pnc]
                     (->> pnc
                          (hsel/select (hsel/child (hsel/tag :a)))
                          last))
                   (select-comments-link [pnc]
                     (->> pnc select-comments
                          :attrs :href))
                   (select-comments-count [pnc]
                     (some->> pnc select-comments
                              :content first
                              (re-find #"\d+") Integer/parseInt))]
             (->> fpp
                  puccih/as-hickory
                  (hsel/select (hsel/child
                                (hsel/follow-adjacent
                                 (hsel/class "athing")
                                 (hsel/tag :tr))))
                  (map #((juxt select-score
                               select-age
                               select-comments-link
                               select-comments-count) %))
                  (map #(zipmap [:score
                                 :age
                                 :comments-link
                                 :comments-count] %))))]
    (map merge lt pc)))


(defrecord Stor []
  component/Lifecycle
  (start [component]
    (-> component
        (assoc :!hn (atom [])
               :!tw (atom []))))
  (stop [component]
    (-> component
        (dissoc :!hn :!tw))))

(defn new-stor
  []
  (map->Stor {}))

(defn update-stor
  [{:keys [!hn !tw]}]
  (reset! !tw
          (let [{:keys [tw-users]} (-> "resources/newsconfig.edn"
                                       slurp edn/read-string)]
            (->> tw-users
                 (map (juxt identity (comp vec get-twts-from)))
                 (mapv (partial zipmap [:username :tweets])))))
  (reset! !hn
          (vec (get-hn-frontpage)))
  nil)

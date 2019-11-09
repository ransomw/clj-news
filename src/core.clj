(ns core
  (:import java.util.Base64)
  (:require [clojure.edn :as edn]
            [clojure.set :refer [rename-keys]]
            [clj-http.client :as http-c]
            [cheshire.core :as jcat]
            [tick.alpha.api :as tick-a]
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

(defn get-stars-from
  [github-username]
  (let [github-auth
        (-> "resources/newsconfig.edn"
            slurp edn/read-string
            :github-auth)
        default-api-url "https://api.github.com"
        starred-repo?
        (fn
          [event-data]
          (and (= "WatchEvent" (get event-data "type"))
               (= "started" (some-> event-data
                                    (get "payload")
                                    (get "action")))))
        desired-repo-info
        {"full_name" :name
         "description" :description
         "html_url" :url-github
         "homepage" :url-home
         "git_url" :url-get
         "stargazers_count" :count-stars
         }
        get-repo-info
        (fn [repo-name]
          (let [res (clj-http.client/get
                     (str default-api-url
                          "/repos/" repo-name)
                     {:basic-auth github-auth})
                ;; no error handling
                repo-info-body (:body res)
                ri (jcat/parse-string repo-info-body)]
            (->> (rename-keys ri desired-repo-info)
                 (filter (fn [[key _]] (keyword? key)))
                 (into {}))))
        loc (str default-api-url
                 "/users/" github-username "/events/public")
        res (clj-http.client/get loc {:basic-auth github-auth})
        ;; no error handling
        user-info-body (:body res)
        star-events
        (filter
         starred-repo?
         (jcat/parse-string user-info-body))
        star-event-names
        (map (fn [star-event]
               (-> star-event
                   (get "repo")
                   (get "name")))
             star-events)
        star-event-repo-infos
        (map get-repo-info star-event-names)
        stamped-star-event-infos
        (map (fn [star-event star-event-repo-info]
               (-> star-event-repo-info
                   (assoc :star-time
                          (-> star-event
                              (get "created_at")
                              tick-a/offset-date-time))))
             star-events star-event-repo-infos)
        ]
    star-event-repo-infos
    ))

(defrecord Stor []
  component/Lifecycle
  (start [component]
    (-> component
        (assoc :!hn (atom [])
               :!tw (atom [])
               :!gh (atom []))))
  (stop [component]
    (-> component
        (dissoc :!hn :!tw :!gh))))

(defn new-stor
  []
  (map->Stor {}))

(defn update-stor
  [{:keys [!hn !tw !gh]}]

  (reset! !gh
          (let [{:keys [gh-users]} (-> "resources/newsconfig.edn"
                                       slurp edn/read-string)]
            (->> gh-users
                 (map (juxt identity (comp vec get-stars-from)))
                 (mapv (partial zipmap [:username :stars])))))

  (reset! !tw
          (let [{:keys [tw-users]} (-> "resources/newsconfig.edn"
                                       slurp edn/read-string)]
            (->> tw-users
                 (map (juxt identity (comp vec get-twts-from)))
                 (mapv (partial zipmap [:username :tweets])))))
  (reset! !hn
          (vec (get-hn-frontpage)))
  nil)

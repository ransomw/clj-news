(ns newsfeed.core
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))


(enable-console-print!)

(defn twitter-user
  [info]
  (let [!tweets? (r/atom false)]
    (fn [info]
      [:div.user
       [:div.info {:on-click #(swap! !tweets? not)}
        [:span.name (:username info)]]
       (when @!tweets?
         [:div.tweets
          (for [[idx tweet] (map list (range) (:tweets info))]
            ^{:key idx}
            [:div.tweet
             [:span (:text tweet)]])])])))

(defn twitter
  [twitter-infos]
  [:div.twitter
     [:h5 "twitter"]
     (for [info twitter-infos]
       ^{:key (:username info)}
       [twitter-user info])])

(defn hn
  [hn-items]
  [:div.hn
   [:h5 "hn"]
   (for [item hn-items]
     ^{:key (:link item)}
     [:div.newsflash
      [:a {:href (:link item)
           :target "_blank"}
       (:text item)]
      [:span.points (:score item)]])])

(defn root
  [!news]
  (let [news @!news]
    [:div.newsfeed
     [:h2 "newsfeed"]
     [:section.social
      [:h3 "social"]
      [:div.social
       [:div.outlet [twitter (:twitter news)]]
       [:div.outlet [hn (:hn news)]]]]]))

(defn render
  [!news]
  (r/render
   [root !news ]
   (js/document.getElementById "app")))

(defn main
  []
  (let [!news (r/atom {})]
    (go (let [{:keys [success body]
               :as res} (<! (http/get "/api/news"))]
          (when success
            (swap! !news merge body))))
    (render !news )))

(main)

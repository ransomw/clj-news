(ns newsfeed.core
  (:require [reagent.core :as r]
            [tick.alpha.api :as tick-a]
            [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]
            [cljs.reader :as reader]
            [oz.core :refer [vega-lite]]
            ))


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

(defn github-stars-user-stars
  [star]
  [:li
   {:style {:margin-bottom "1em"}}
   [:div.name
    [:span.label "name: "]
    [:span.text (:name star)]]
   [:div.description
    [:span.label "description: "]
    [:span.text (:description star)]]
   [:div.count-stars
    [:span.label "number of stars: "]
    [:span.text (str (:count-stars star))]]
   [:div.urls
    [:a {:href (:url-github star)
         :style {:margin-right "1em"}} "github"]
    (when-not (empty? (:url-home star))
      [:a {:href (:url-home star)} "home"])
    [:a {:href (:url-get star)
         :style {:margin-left "1em"}} "clone"]]])

(defn github-stars-user
  [stars]
  (let [!stars? (r/atom false)]
    (fn [stars]
      [:div.star
       [:div
        [:div.info {:on-click #(swap! !stars? not)}
         [:span.name (:username stars)]]
        (when @!stars?
          [:ul
           (for [star (:stars stars)]
             ^{:key (:name star)}
             [github-stars-user-stars star])])]])))

(defn github-stars
  [all-stars]
  [:div.gh-stars
   [:h5 "stars"]
   (for [stars all-stars]
     ^{:key (:username stars)}
     [github-stars-user stars])])

(defn geo-weather-location
  [loc]
  (let []
    (fn [loc]
      [:div.scenic
       [:div
        [:div.disp
         {:style {:display "flex"
                  :justify-content "center"
                  :align-items "center"
                  :position "relative"}}
         [:span
          {:style {:position "absolute"
                   :top "0" :left "0"}}
          (str
           (letfn [(kelvin-to-ferenheit [K]
                     (+ (* (- K 273.15) (/ 9 5))
                        32)
                     )]
             (kelvin-to-ferenheit
              (js/parseInt (:temperature loc))))
           "°F")]
         (:weather loc)]
        [:footer [:span (:city-name loc)]]]])))

(defn geo-weather-locs->temp-plot-data
  [locs]
  (let [hours-diff
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
     [] locs)))

(defn geo-weather
  [locs]
  [:div.geo-weather
   [:h5 "World-Wide Weather"]
   (for [loc locs]
     ^{:key (:city-name loc)}
     [geo-weather-location loc])
   [:div
    [:div [:span (str "temperatures"
                      " offset in hours from common reference"
                      " in local time")]]
    [vega-lite {:data {:values
                       (geo-weather-locs->temp-plot-data locs)}
                :encoding {:x {:field "hours"}
                           :y {:field "temp"}
                           :color {:field "city" :type "nominal"}}
                :mark "line"}]
    ]])

(defn root
  [!news]
  (let [news @!news]
    [:div.newsfeed
     [:h2 "newsfeed"]
     [:section.social
      [:h3 "social"]
      [:div.social
       [:div.outlet [twitter (:twitter news)]]
       [:div.outlet [hn (:hn news)]]]]
     [:section.seasons
      [:h3 "cycles"]
      [:div.computing
       [:div.outlet [github-stars (:gh news)]]]
      [:div.weather
       [:div.outlet [geo-weather (:geo-weather news)]]]
      ]]))

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

(reader/register-tag-parser!
 'time/date-time tick-a/date-time)

(main)

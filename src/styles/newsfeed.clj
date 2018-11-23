(ns styles.newsfeed
  (:require [garden.def :refer [defstylesheet defstyles]]
            [garden.stylesheet :as sty]))

(def hn
  [:.hn
   [:.newsflash {:display "flex"
                 :flex-direction "row"
                 :justify-content "space-between"
                 :margin-bottom ".75em"
                 :margin-left "2em"
                 :margin-right ".5em"}]])

(def twitter
  [:.twitter
   [:.user {:margin-bottom "1em"}
    [:.info {:cursor "pointer"}]
    [:.tweets {:border-top "solid 1px black"
               :padding ".5em"}]]])

(def social
  [:.social {:display "grid"
             :grid-template-columns "1fr 1fr"}
   [:h5 {:font-family "Overlock-Regular"
         :font-size "1.2em"
         :text-align "center"
         :margin-top ".5em"}]
   [:.outlet {:margin "1rem"
              :border "solid .1rem black"
              :border-radius ".1rem"
              :padding "1rem"}]
   twitter hn])

(def root
  [:.newsfeed {:font-size "1.1rem"}
   [:h2 {:color "#F09000"
         :font-family "AlexBrush-Regular"
         :text-align "center"
         :font-size "2em"}]
   [:section
    [:h3 {:color "#0090F0"
          :font-family "italiana-regular"
          :margin-left "2em"}]
    social]])

(defstylesheet newsfeed
  {:output-to "resources/public/css/compiled/newsfeed.css"}
  [root])

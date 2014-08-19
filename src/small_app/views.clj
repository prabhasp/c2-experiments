(ns small-app.views
  (:use [hiccup.page :only [html5 include-css]]
        [c2.layout.histogram :only [histogram]]
        [c2.core :only [unify]])
  (:require [clojure.string :as str]
            [c2.scale :as scale]
            [clj-http.client :as client]
            [c2.svg :as svg]))

(defn gen-page-head
  [title]
    [:head
       [:title (str "Small-app Homepage " title)]
          (include-css "/css/style.css")])



(defn- style [& info]
  {:style (.trim (apply str (reduce #(conj %1
                                           (-> %2 first name) ":" (last %2) ";")
                                    []
                                    (partition 2 info))))})

(defn- extract-data [chart-data]
  (let [{:keys [data field_xpath]} chart-data
        na-fix (fn [s] (if (nil? s) "NA" (str s)))]
    (into {} (for [data-item data]
               [(na-fix ((keyword field_xpath) data-item))
                (:count data-item)]))))

(defn- extract-data-for-histogram [chart-data bins]
  (let [{:keys [data field_xpath]} chart-data
        qn-key (keyword field_xpath)
        non-nil-data (remove (fn [el] (nil? (qn-key el))) data)
        data-with-numbers (map (fn [el]
                                 (assoc el qn-key (Integer. (qn-key el))))
                               non-nil-data)
        binned-data (histogram data-with-numbers :value qn-key :bins bins)]
    (for [data-item binned-data]
      [(:x (meta data-item))
       (:dx (meta data-item))
       (apply + (map :count data-item))])))

;(extract-data-for-histogram (data-for-qn "q8"))

(defn numeric-chart
  [chart-data]
  (let [chart-width 700 chart-height 300 bins 10
        margin 50 small-margin 2
        extracted-data (extract-data-for-histogram chart-data bins)
        {:keys [field_label field_xpath]} chart-data
        x-series (map first extracted-data)
        dx-series (map second extracted-data)
        y-series (map last extracted-data)
        xmin (apply min x-series)
        xmax (+ (apply max x-series) (last dx-series))
        x-scale (scale/linear :domain [xmin xmax]
                              :range [0 chart-width])
        y-scale (scale/linear :domain [0 (apply max y-series)]
                              :range [0 chart-height])
        bin-width (- (/ chart-width bins) small-margin)
        x-ticks (take-nth 2 (rest x-series))]
    [:table#histogram.table-bordered
     [:thead [:tr [:th field_label]]]
     [:tbody [:tr [:td
        [:svg {:width (+ margin chart-width) :height (+ margin chart-height)}
         [:g.chart {:transform (svg/translate [margin 0])}
          [:g
          (unify extracted-data
                 (fn [[x dx y]]
                   (let [x-scaled (float (x-scale x))
                         y-scaled (float (y-scale y))]
                     [:g.bars {:transform
                              (svg/translate [x-scaled
                                              (- chart-height y-scaled)])}
                      [:rect {:x 1
                              :height y-scaled
                              :width bin-width}]])))]
          [:g.axis {:transform (svg/translate [0 chart-height])}
          (unify x-ticks
                   (fn [x]
                   [:g.tick {:transform (svg/translate
                                         [(float (x-scale x)) 0])
                             :style "opacity: 1;"}
                    [:text {:y 25 :text-anchor "middle"} x]
                    [:line.tick {:y2 10 :x2 0}]]))
           [:line {:x1 0 :x2 chart-width}]]
          ]]]]]]))

(defn category-chart [chart-data]
  (let [bar-max-width 500
        bar-height 20 other-width 50
        {:keys [field_xpath field_label]} chart-data
        extracted-data (extract-data chart-data)
        s (if (> (count extracted-data) 0)
          (scale/linear :domain [0 (apply max (vals extracted-data))]
                        :range [0 bar-max-width]))]
    [:table#bars.table-bordered
     [:thead
      [:tr
       [:th {:colspan 2} field_label] [:th "Count"]]]
     [:tbody
      (unify extracted-data (fn [[label val]]
                              [:tr
                               [:td label]
                               [:td [:div#bar (style :height (str bar-height "px")
                                                     :width (str (float (s val)) "px")
                                                     :background-color "grey"
                                                     :opacity 0.6
                                                     :margin "2px")]]
                               [:td val]]))]]))

(defn data-for-qn [qn]
  (:body (client/get
; NOTE: to start the ring server up for the first time, the API should hit ona
; once the server is up, you can uncomment the 3000 line so that subsequent
; requests (which happen at compile time) are faster.
;          (str "http://localhost:3000/" qn ".json")
          (str "https://ona.io/api/v1/charts/196.json?field_name=" qn)
          {:as :json})))

(def n-data-5 (data-for-qn "q5"))
(def n-data (data-for-qn "q8"))
(def c-data-18 (data-for-qn "q18"))
(def c-data (data-for-qn "q18a"))

(defn home-page
  []
  (html5
   (gen-page-head "Home")
   [:div [:div#container
     (category-chart c-data-18)
     (category-chart c-data)
     (numeric-chart n-data-5)
     (numeric-chart n-data)]]))

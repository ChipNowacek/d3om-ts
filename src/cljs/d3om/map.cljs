(ns d3om.map
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [d3om.core :as c :refer [printf format]]
            [d3om.components :as comp]
            [clojure.browser.repl]
            [cljs.core.async :refer [<! chan put! sliding-buffer sub pub timeout]]
            [om.core :as om :include-macros true]
            [sablono.core :as h :refer-macros [html]]
            )
  )

(enable-console-print!)

(def app-state
  (atom {:oil-data (sorted-map) ; {date1 {:production {"CA" 232 ...} "SpotPrice" 23} date2 {...}
         }))

(def TIME_FORMAT
  (-> js/d3 .-time (.format "%Y/%m")))

(def NUMBER_FORMAT
  (-> js/d3 (.format ",d")))

(defn map-chart
  "snapshot => of oil :production data"
  [snapshot owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [width 960 height 500
            projection (-> js/d3 .-geo .albersUsa (.scale 1000) (.translate #js [(/ width 2) (/ height 2)]))]
        {:width width :height height
         :path (-> js/d3 .-geo .path (.projection projection))
         :color (-> js/d3 .-scale .linear
                  (.range #js ["green" "red"]))
         :comm (chan)
         :us nil ;topojson data
         :svg nil
         }))

    om/IWillMount
    (will-mount [_]
      (c/shared-async->local-state owner [:oil-prod-max-val]) ;subscribe to updates to shared-async-state
      (let [{:keys [comm]} (om/get-state owner)]
        (-> js/d3 (.json "/data/us-named.json"
                      (fn [error us]
                        (put! comm [:us us])))) ;callbacks are not part of the render phase, need to relay
        (go (while true
              (let [[k v] (<! comm)]
                (case k
                  :us (om/update-state! owner #(assoc % :us v))
                  ))))))

    om/IDidMount
    (did-mount [_]
      (let [{:keys [width height comm]} (om/get-state owner)
            svg (-> js/d3 (.select "#map") (.append "svg")
                  (.attr "width" width) (.attr "height" height))]
        (om/update-state! owner #(assoc % :svg svg))))

    om/IRender
    (render [_]
      (html
        [:div#map]))

    om/IDidUpdate
    (did-update [_ prev-snapshot prev-state]
      (let [{:keys [svg us path color oil-prod-max-val]} (om/get-state owner)]
        (when us
          (-> color (.domain #js [0 oil-prod-max-val]))
          (let [states (-> svg (.selectAll ".state")
                         (.data (-> js/topojson (.feature us (-> us .-objects .-states)) .-features)))]
            (-> states
                (.attr "fill" (fn [d-]
                                 (let [code (-> d- .-properties .-code)
                                       prod (or (get snapshot code) 0)]
                                   (color prod))))
              (.enter)
                (.append "path")
                  (.attr "class" (fn [d-] (str "state " (-> d- .-properties .-code))))
                  (.attr "fill" (fn [d-]
                                  (let [code (-> d- .-properties .-code)
                                        prod (or (get snapshot code) 0)]
                                    (color prod))))
                  (.attr "d" path)
                (.append "title"))
            (-> states (.select "path title")
                (.text (fn [d-]
                               (let [code (-> d- .-properties .-code)
                                     prod (or (get snapshot code) 0)]
                                 (NUMBER_FORMAT prod)))))
            ))))
    ))

(defn- load-datas
  "to-load => {:keys [csv-file row-fn get-fn]}"
  [result-comm & to-loads]
  (when-let [{:keys [csv-file row-fn get-fn]} (first to-loads)]
    (-> js/d3 (.csv csv-file)
      (.row row-fn)
      (.get (fn [error rows]
              (get-fn error rows)
              (apply load-datas result-comm (rest to-loads)))))))

(defn- parse-nums
  [m]
  (->> m
    (map (fn [[k v]]
           (when-not (= k "Date")
	            [k (js/parseFloat v)])) ,,,)
    (into {} ,,,)))

(defn- load-all
  [result-comm]
  (load-datas result-comm
    {:csv-file "/data/us-oil-prod.csv"
     :row-fn (fn [d-]
               [(-> TIME_FORMAT (.parse (-> d- .-Date)))
                {:production (parse-nums (js->clj d-))}])
     :get-fn (fn [error rows] (put! result-comm [:oil-prod (into {} rows)]))}
    {:csv-file "/data/us-oil-data.csv"
     :row-fn (fn [d-]
               [(-> TIME_FORMAT (.parse (-> d- .-Date)))
                (parse-nums (js->clj d-))])
     :get-fn (fn [error rows] (put! result-comm [:oil-data (into {} rows)]))}))

(defn snapshots->seriess
  [snapshots state]
  (let [palette (js/Rickshaw.Color.Palette. #js {:scheme "colorwheel"})]
    [(let [color (-> palette (.color))
           data (mapv (fn [[date snapshot]]
                        {:x (-> date .getTime (/ 1000))
                         :y (get snapshot "SpotPrice")}) snapshots)] ;hack to handle negatives in stacked graph
       {:color color :name "SpotPrice" :data data})]))

(defn app
  [cursor owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)})

    om/IWillMount
    (will-mount [_]
      (c/shared-async->local-state owner [:ts])
      (let [{:keys [comm]} (om/get-state owner)
            {:keys [publisher]} (om/get-shared owner)]
        (load-all comm)
        (go (while true
                (let [[k v] (<! comm)]
                  (case k
                    :oil-prod (let [oil-prod-max-val (->> v (mapcat #(-> % second :production vals) ,,,) (apply max ,,,))]
                                (put! publisher [:oil-prod-max-val oil-prod-max-val])
                                (om/transact! cursor [:oil-data] #(merge-with merge % v))
                                (let [first-ts (-> @cursor :oil-data keys first)]
                                  (om/set-state! owner :ts first-ts)))
                    :oil-data (om/transact! cursor [:oil-data] #(merge-with merge % v)))))))
      (let [subscriber (chan)
            {:keys [publication publisher]} (om/get-shared owner)]
        (sub publication :ts subscriber)
        (go (while true
              (let [[k v] (<! subscriber)]
                (case k
                  :ts (om/update! cursor [:ts :current] v)))))))

    om/IRenderState
    (render-state [_ {:keys [ts]}]
      (html
        [:div
         [:div
          [:h4 "Monthly Oil Production (1000s Barrels) - " (TIME_FORMAT (or ts (js/Date. 0)))]
          [:p (om/build map-chart (get-in cursor [:oil-data ts :production]))]]
         #_[:div (om/build comp/ghetto-timeslider (-> cursor :oil-data) {:fn keys})]
         (let [legend-id (str "legend-" (gensym))]
           [:div
            (om/build comp/timeseries (-> cursor :oil-data) {:opts {:width 850 :timeslider? true :legend-id legend-id
                                                                    :get-seriess snapshots->seriess}})
            [:div.timeseries-legend {:id legend-id}]])

         [:p [:h5 "Data Sources"]
          [:ul
           [:li [:a {:href "http://www.eia.gov/dnav/pet/pet_crd_crpdn_adc_mbbl_m.htm"} "Monthly Production (1000s Barrels)"]]
           [:li [:a {:href "http://www.eia.gov/dnav/pet/hist/LeafHandler.ashx?n=PET&s=RWTC&f=D"} "Cushing, OK WTI Spot Price FOB."]]

          ]]
         ]))
    ))

(defn main []
  (om/root app app-state {:target (. js/document (getElementById "app"))
                          :shared (let [publisher (chan)]
                                    {:publisher publisher
                                     :publication (pub publisher first)
                                     :init {:oil-prod-max-val 0
                                            :ts (js/Date. 0)}
                                     })})
  )

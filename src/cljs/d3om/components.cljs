(ns d3om.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [d3om.core :as c :refer [printf format]]
            [clojure.browser.repl]
            [cljs.core.async :refer [<! chan put! sliding-buffer sub pub timeout]]
            [om.core :as om :include-macros true]
            [sablono.core :as h :refer-macros [html]]
            )
  )

(defn- timeslider-update
  "comm => for communicating :ts-index update
   ts-index => optionally set"
  [d3-select ts-index- comm x-scaler & {:keys [ts-index] :or {ts-index 0}}]
  (let [total-width (.attr d3-select "width")
        cursor-width 8]
    (-> d3-select (.selectAll ".time-cursor")
        (.data #js [ts-index-])
        (.call (fn [d] (.attr d "x" (x-scaler ts-index))))
      (.enter) (.append "rect")
        (.attr "class" "time-cursor")
        (.attr "x" #(.-x %)) (.attr "y" 0) (.attr "width" cursor-width) (.attr "height" "100%")
        (.call (-> (js/d3.behavior.drag)
                 (.on "drag" (fn [d]
                               (this-as my-this
                                 (-> js/d3 (.select my-this)
                                   (.attr "x" (fn [_] (set! (.-x d) (-> (.-x js/d3.event) (max 0) (min (- total-width cursor-width))))))))
                               (put! comm [:ts-index (int (.invert x-scaler (.-x d)))]))))))))

(defn- timeslider
  "must be a child component (e.g. of time-series).
   to-target => svg selection for the timeslider to overlay
   comm => for receiving :rerender msg if/when parent rerenders as well as internal comm"
  [dates owner {:keys [to-target comm] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:ts-index- #js {:x 0}
       :dates- #js []
       :x-scaler nil})
    om/IWillMount
    (will-mount [_]
      (let [subscriber (chan)
            {:keys [publisher publication]} (om/get-shared owner)
            {:keys [ts-index- playing?]} (om/get-state owner)]
        (doseq [k [:ts]] (sub publication k subscriber))
        (go (while true
              (let [[t v] (<! subscriber)]
                (case t
                  :ts (let [{:keys [x-scaler dates-]} (om/get-state owner)]
                        (timeslider-update (to-target) ts-index- comm x-scaler :ts-index (.indexOf dates- v)))))))
        (go (while true
              (let [[t v] (<! comm)]
                (case t
                  :ts-index (let [{:keys [dates-]} (om/get-state owner)]
                              (put! publisher [:ts (nth dates- v)]))
                  :rerender (let [target (to-target)
                                  width (.attr target "width")
                                  x-scaler (-> js/d3.scale (.linear) (.domain (array 0 v)) (.range (array 0 (- width 8))))] ; #js literal doesn't work here. See https://groups.google.com/forum/#!topic/clojurescript/ZVvzN0-FSyU
                              (om/update-state! owner #(merge % {:x-scaler x-scaler :ts-count v}))
                              (timeslider-update target ts-index- comm x-scaler))))))
            ))
    om/IRender
    (render [_]
      (html [:div]))

    om/IDidUpdate
    (did-update [_ prev-dates _]
      (when (or (empty? (om/get-state owner :dates-)) (not= dates prev-dates)) ;not set yet, or changed.
        (om/set-state! owner :dates- (clj->js dates))))
    ))

(defn timeseries
  "OM component built on Rickshaw.js, with optional time slider.
   dependent-ks => (optional) from shared-async-state which if updated, should triggers a rerender.
   get-seriess => (fn [snapshots state] ..) ;returns 2d data series in Rickshaw.js format
   timeslider? => (optional) overlay timeslider. Publishes timestamp :ts to shared-async-state"
  [snapshots owner {:keys [get-seriess legend-id dependent-ks width timeslider?] :as opts
                    :or {dependent-ks [] width 960}}]
  (assert (and legend-id get-seriess))
  (reify
    om/IInitState
    (init-state [_]
      {:id (str "timeseries-" (gensym))
       :seriess- #js []
       :graph nil
       :comm (chan)
       :timeslider-comm (chan) ;write-only
       })

    om/IWillMount
    (will-mount [_]
      (c/shared-async->local-state owner dependent-ks)
      (let [{:keys [comm]} (om/get-state owner)]
        (go (while true
              (let [[t v] (<! comm)]
                (case t
                  :state-update (om/update-state! owner #(merge % v))))))))

    om/IDidMount
    (did-mount [_]
      (let [{:keys [seriess- comm timeslider-comm id] :as state} (om/get-state owner)
            graph (js/Rickshaw.Graph. #js {:element (js/document.getElementById id)
                                           :series seriess-
                                           :width width :height 200
                                           :renderer "line" :min "auto" :stroke true :preserve true})
            legend (js/Rickshaw.Graph.Legend. #js {:graph graph
                                                   :element (. js/document (getElementById legend-id))})
            x-axis (js/Rickshaw.Graph.Axis.Time. #js {:graph graph
                                                      :ticksTreatment "glow"})
            y-axis (js/Rickshaw.Graph.Axis.Y. #js {:graph graph})
            hover-detail (js/Rickshaw.Graph.HoverDetail. #js {:graph graph})]
        (put! comm [:state-update {:graph graph :legend legend}])))

    om/IRenderState
    (render-state [_ {:keys [timeslider-comm id] :as state}]
      (html
        [:div.timeseries-graph {:id id}
         (when timeslider?
           (om/build timeslider snapshots {:opts {:to-target #(js/d3.select (str "#" id " svg"))
                                                  :comm timeslider-comm}
                                           :fn #(-> % keys vec)}))]))

    om/IDidUpdate
    (did-update [_ prev-snapshots prev-state]
      (let [{:keys [seriess- graph legend timeslider-comm] :as state} (om/get-state owner)
            recalc-data-series? (or (not= snapshots prev-snapshots)
                                    (some #(apply not= %) (for [k dependent-ks] [(k state) (k prev-state)])))]
        (when recalc-data-series?
          (put! timeslider-comm [:rerender (dec (count snapshots))])
          (-> seriess-
            (.splice 0 (.-length seriess-))
            (.push.apply seriess- (clj->js (get-seriess snapshots state))))
          (doseq [component [graph legend]]
            (-> component (.render))))))
    ))

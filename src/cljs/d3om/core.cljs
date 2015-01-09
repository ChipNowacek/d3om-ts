(ns d3om.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! chan put! sliding-buffer sub pub]]
            [goog.string :as gstring]
            [goog.string.format]))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply gstring/format fmt args))

(defn printf
  "Prints formatted output, as per format"
  [fmt & args]
  (print (apply format fmt args)))

(defn shared-async->local-state
  "helper method for watching & copying shared-async-state to local state.
   inits selected ks from shared-state (:init) to local state"
  [owner ks]
  (let [subscriber (chan)
        {:keys [init publication]} (om/get-shared owner)]
    (assert (and init publication))
    (om/update-state! owner #(merge % (select-keys init ks)))
    (doseq [k ks]
      (sub publication k subscriber))
    (go (while true
          (let [[k v] (<! subscriber)]
            (om/set-state! owner [k] v))))))








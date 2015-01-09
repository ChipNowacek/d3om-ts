(ns d3om.dev
  (:require [environ.core :refer [env]]
;            [net.cgrand.enlive-html :refer [set-attr prepend append html]]
            [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]
            [leiningen.core.main :as lein]))

(def is-dev? (env :is-dev))

(defn prepend-devmode-html []
  (when is-dev?
    (list [:script {:type "text/javascript" :src "/js/out/goog/base.js"}]
          [:script {:type "text/javascript" :src "/js/react.v0.11.1.js"}])))

(defn append-devmode-html [cljs-ns]
  (when is-dev?
    (list [:script {:type "text/javascript"} 
           (str "goog.require('" cljs-ns "')")])))

(defn browser-repl []
  (piggieback/cljs-repl :repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)))

(defn start-figwheel []
  (future
    (print "Starting figwheel.\n")
    (lein/-main ["figwheel"])))

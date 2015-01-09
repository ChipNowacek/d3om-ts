(ns d3om.handler
  (:require [clojure.java.io :as io]
            [d3om.dev :refer [is-dev? prepend-devmode-html append-devmode-html browser-repl start-figwheel]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]

            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [hiccup
             [core :refer [html]]
             [page :refer [html5 include-js include-css]]])
  )

(defn include-csss [& styles]
  (apply include-css (map (partial str "css/") styles)))

(defn include-jss [& scripts]
  (apply include-js (map #(if (.startsWith %1 "http") %1 (str "js/" %1)) scripts)))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defroutes api-routes
  (GET "/map" []
       (html
         [:head
          [:link {:rel "icon" :href "img/favicon.ico" :type "image/x-icon"}]
          [:title "LiquidLandscape Oil Map"]
          (include-csss "bootstrap.css" "rickshaw.min.css" "map.css")]
         [:body
          (prepend-devmode-html)
          [:p#app]
          (include-jss "d3.v3.min.js" "topojson.v1.min.js" "rickshaw.min.js" "app.js")
          (append-devmode-html "d3om.dev.map")
          ]))

  (route/resources "/")
  (route/not-found "not found"))


(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'api-routes))
    (api api-routes)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (if is-dev? (start-figwheel))
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port port
                          :join? false}))))
  server)

(defn -main [& [port]]
  (run port))


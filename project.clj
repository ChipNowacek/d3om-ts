(defproject d3om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"


  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.494"]
                 ;[org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [ring "1.6.0-RC2"]
                 [compojure "1.5.2"]
                 [enlive "1.1.6"]
                 [org.omcljs/om "0.9.0"]
                 [lein-figwheel "0.5.10"]
                 [environ "1.1.0"]
                 [com.cemerick/piggieback "0.2.1"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [weasel "0.7.0"]
                 [leiningen "2.5.0"]
                 [http-kit "2.2.0"]
                 [prismatic/om-tools "0.4.0"]
                 [cljsjs/react "15.5.0-0"]
                 [cljsjs/react-dom "15.5.0-0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "0.2.6"]
                 [sablono "0.8.0"]
                 [cljs-ajax "0.5.8"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-environ "1.1.0"]
            [lein-tar "3.2.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "d3om.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    true
                                        :preamble      ["react/react.min.js"]
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:repl-options {:init-ns d3om.handler
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.5.10"]]

                   :figwheel {:http-server-root "public"
                              ;:port 3449
                              :css-dirs ["resources/public/css"]}

                   :env {:is-dev true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}}


             :uberjar {:hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})

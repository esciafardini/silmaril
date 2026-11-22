(ns silmaril.clj.server
  "Ring server"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.file :refer [wrap-file]]
            [silmaril.clj.routes :as routes]))


(def app (-> #'routes/routes (wrap-file "public") wrap-content-type))

(defonce server (atom nil))

(defn start! [& {:keys [port] :or {port 8888}}]
  (reset! server (jetty/run-jetty #'app {:port port :join? false})))

(defn stop! []
  (some-> @server .stop)
  (reset! server nil))

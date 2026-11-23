(ns silmaril.clj.routes 
  (:require
    [silmaril.clj.model :as model]))

(defn edn-response [data]
  {:status 200
   :headers {"content-type" "application/edn; charset=utf-8"}
   :body (pr-str data)})

(defn routes [{:keys [request-method uri]}]
  (case [request-method uri]
    [:get "/api/chapters"] (edn-response (model/get-all-chapters))
    {:status 404 :body "not found"}))

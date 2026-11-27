(ns silmaril.clj.routes 
  (:require
    [silmaril.clj.model :as model]))

(defn edn-response [data]
  {:status 200
   :headers {"content-type" "application/edn; charset=utf-8"}
   :body (pr-str data)})

(def bad-request {:status 400 :body "missing or invalid n"})

(defn routes [{:keys [request-method uri query-params]}]
  ;; query-params come from wrap-params: string keys, string values
  (let [n (some-> (get query-params "n") parse-long)]
    (case [request-method uri]
      [:get "/api/chapters"] (edn-response (model/get-all-chapters))
      ;; /api/chapter?n=21
      [:get "/api/chapter"]  (if n (edn-response (model/find-entries-for-chapter n)) bad-request)
      ;; /api/page?n=4
      [:get "/api/page"]     (if n (edn-response (model/find-entries-for-page n)) bad-request)
      {:status 404 :body "not found"})))

(ns silmaril.cljs.http
  (:require [goog.net.XhrIo :as xhr]
            [cljs.reader :as reader]))

(defn fetch [url on-success on-error]
  (xhr/send
    url
    (fn [event]
      (let [^js req (.-target event)]
        (if (.isSuccess req)
          (on-success (reader/read-string (.getResponse req)))
          (on-error (.getLastError req)))))
    "GET"
    nil
    #js {"Accept" "application/edn"}))

(ns silmaril.cljs.app
  (:require [cljs.reader :as reader]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]))

(defonce state (r/atom {:status :idle :chapters []}))

(defn fetch-edn [url]
  (-> (js/fetch url #js {:headers #js {"Accept" "application/edn"}})
      (.then (fn [resp]
               (if (.-ok resp)
                 (.text resp)
                 (throw (js/Error. (str url " -> " (.-status resp)))))))
      (.then reader/read-string)))

(defn load-chapters! []
  (swap! state assoc :status :loading)
  (-> (fetch-edn "/api/chapters")
      (.then #(swap! state assoc :status :loaded :chapters %))
      (.catch #(swap! state assoc :status :error :error (.-message %)))))

(defn chapter-item [{:chapter/keys [name start-page]}]
  [:li
   [:span name]
   (when start-page [:small " p. " start-page])])

(defn chapter-list []
  (let [{:keys [status chapters error]} @state]
    (case status
      (:idle :loading) [:p "Loading…"]
      :error           [:p "Failed to load chapters: " error]
      :loaded          [:ol
                        (for [{:db/keys [id] :as c} chapters]
                          ^{:key id} [chapter-item c])])))

(defn app []
  [:main
   [:h1 "The Silmarillion"]
   [chapter-list]])

(defonce root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn ^:dev/after-load mount! []
  (rdc/render @root [app]))

(defn init []
  (mount!)
  (load-chapters!))

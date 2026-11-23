(ns silmaril.cljs.app
  (:require [silmaril.cljs.http :as http]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]))

(defonce state (r/atom {:status :idle :chapters [] :entries [] :entry {}}))

(defn load-chapters! []
  (swap! state assoc :status :loading)
  (http/fetch "/api/chapters"
              #(swap! state assoc :status :success :chapters %)
              #(swap! state assoc :status :error :error %)))

(defn chapter-item [{page :chapter/start-page chapter-name :chapter/name}]
  [:li [:span chapter-name] [:small " (p " page ")"]])

(defn chapter-list []
  (let [{:keys [status chapters error]} @state]
    (case status
      (:idle :loading) [:p "Loading…"]
      :error [:p "Failed to load: " error]
      :success [:ol (map
                     (fn [{:db/keys [id] :as c}]
                       ^{:key id} [chapter-item c])
                     chapters)])))

(defn app []
  [:main
   [:h1 "The Silmarillion"]
   [chapter-list]])

;;; Shadow-CLJS + Reagent
(defonce root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn ^:dev/after-load mount! []
  (rdc/render @root [app]))

(defn init []
  (mount!)
  (load-chapters!))

(ns silmaril.cljs.app
  (:require [silmaril.cljs.http :as http]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]))

(defonce state (r/atom {:status :idle :chapters [] :entries [] :entry {} :chapter nil :page nil}))

(defn load-chapters! []
  (swap! state assoc :status :loading)
  (http/fetch "/api/chapters"
              #(swap! state assoc :status :success :chapters %)
              #(swap! state assoc :status :error :error %)))

(defn load-entries-for-chapter! [chapter-num]
  (swap! state assoc :status :loading)
  (http/fetch (str "/api/chapter?n=" chapter-num)
              #(swap! state assoc :status :success :entries %)
              #(swap! state assoc :status :error :error %)))

(defn chapter-item [{:chapter/keys [start-page position] chapter-name :chapter/name}]
  [:li
   {:on-click (fn [_e]
                (load-entries-for-chapter! position))}
   [:span chapter-name] [:small " (p " start-page ")"]])

(defn chapter-list []
  (let [{:keys [status chapters error]} @state]
    (cond
      chapters [:ol (map
                     (fn [c]
                       ^{:key (:chapter/position c)} [chapter-item c])
                     chapters)]
      (#{:idle :loading} status) [:p "Loading…"]
      (= :error status) [:p "Failed to load: " error])))

(defn entries-list []
  (let [{:keys [entries]} @state]
    [:div
     (map (fn [entry]
            (print entry)
            [:div {:style {:border "2px solid #333333;"}}
             [:h2 (:name entry)]
             [:i (:entry-type entry)]
             [:p (:description entry)]
             [:small (str "First appearance in chapter: p. " (:lowest-page entry))]])
          (rest entries))]))

(defn app []
  (let [{:keys [entries]} @state]
    (print entries)
    [:main
     [:h1 "The Silmarillion"]
     (if (seq entries)
       [entries-list]
       [chapter-list])]))

;;; Shadow-CLJS + Reagent
(defonce root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn ^:dev/after-load mount! []
  (rdc/render @root [app]))

(defn init []
  (mount!)
  (load-chapters!))

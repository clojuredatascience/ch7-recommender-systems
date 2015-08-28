(ns cljds.ch7.slope-one
  (:require [iota :as iota]
            [medley.core :refer [map-vals]]
            [clojure.string :as str]
            [cljds.ch7.data :refer :all]
            [incanter.stats :as s]))

(defn conj-item-difference [dict [i j]]
  (let [difference (-  (:rating j) (:rating i))]
    (update-in dict [(:item i) (:item j)] conj difference)))

(defn collect-item-differences [dict items]
  (reduce conj-item-difference dict
          (for [i items
                j items
                :when (not= i j)]
            [i j])))

(defn item-differences [user-ratings]
  (reduce collect-item-differences {} user-ratings))

(defn summarize-item-differences [related-items]
  (let [f (fn [differences]
            {:mean  (s/mean differences)
             :count (count  differences)})]
    (map-vals f related-items)))

(defn slope-one-recommender [ratings]
  (->> (item-differences ratings)
       (map-vals summarize-item-differences)))


(defn candidates [recommender {:keys [rating item]}]
  (->> (get recommender item)
       (map (fn [[id {:keys [mean count]}]]
              {:item id
               :rating (+ rating mean)
               :count count}))))

(defn weighted-rating [[id candidates]]
  (let [ratings-count (reduce + (map :count candidates))
        sum-rating (map #(* (:rating %) (:count %)) candidates)
        weighted-rating (/ (reduce + sum-rating) ratings-count)]
    {:item id
     :rating weighted-rating
     :count  ratings-count}))

(defn slope-one-recommend [recommender rated top-n]
  (let [already-rated  (set (map :item rated))
        already-rated? (fn [{:keys [id]}]
                         (contains? already-rated id))
        recommendations (->> (mapcat #(candidates recommender %)
                                     rated)
                             (group-by :item)
                             (map weighted-rating)
                             (remove already-rated?)
                             (sort-by :rating >))]
    (take top-n recommendations)))

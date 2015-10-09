(ns cljds.ch7.mahout
  (:require [clojure.java.io :as io]
            [incanter.charts :as c]
            [incanter.core :as i])
  (:import [org.apache.mahout.cf.taste.eval RecommenderBuilder]
           [org.apache.mahout.cf.taste.impl.eval GenericRecommenderIRStatsEvaluator RMSRecommenderEvaluator]
           [org.apache.mahout.cf.taste.impl.model GenericBooleanPrefDataModel]
           [org.apache.mahout.cf.taste.impl.model.file FileDataModel]
           [org.apache.mahout.cf.taste.impl.neighborhood NearestNUserNeighborhood]
           [org.apache.mahout.cf.taste.impl.recommender GenericBooleanPrefUserBasedRecommender GenericUserBasedRecommender]))

(defn load-model [path]
  (-> (io/resource path)
      (io/file)
      (FileDataModel.)))

(defn recommender-builder [n sim]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [nhood (NearestNUserNeighborhood. n sim model)]
        (GenericUserBasedRecommender. model nhood sim)))))

(defn evaluate-rmse [builder model]
  (-> (RMSRecommenderEvaluator.)
      (.evaluate builder nil model 0.7 1.0)))

(defn evaluate-ir [builder model]
  (-> (GenericRecommenderIRStatsEvaluator.)
      (.evaluate builder nil model nil 5
                 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD
                 1.0)
      (bean)))

(defn plot-ir [xs stats]
  (-> (c/xy-plot xs (map :recall stats)
                 :x-label "Neighbourhood Size"
                 :y-label "IR Statistic"
                 :series-label "Recall"
                 :legend true)
      (c/add-lines xs (map :precision stats)
                   :series-label "Precision")
      (c/add-lines xs
                   (map :normalizedDiscountedCumulativeGain stats)
                   :series-label "NDCG")
      (i/view)))

(defn boolean-recommender-builder [n sim]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [nhood (NearestNUserNeighborhood. n sim model)]
        (GenericBooleanPrefUserBasedRecommender.
         model nhood sim)))))

(defn to-boolean-preferences [model]
  (-> (GenericBooleanPrefDataModel/toDataMap model)
      (GenericBooleanPrefDataModel.)))

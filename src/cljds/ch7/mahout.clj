(ns cljds.ch7.mahout
  (:require [clojure.string :as s]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io])
  (:import [org.apache.mahout.cf.taste.impl.model.file FileDataModel]
           [org.apache.mahout.cf.taste.impl.model PlusAnonymousUserDataModel GenericUserPreferenceArray GenericBooleanPrefDataModel]
           [org.apache.mahout.cf.taste.similarity.precompute.example GroupLensDataModel]
           [org.apache.mahout.cf.taste.impl.similarity TanimotoCoefficientSimilarity UncenteredCosineSimilarity LogLikelihoodSimilarity SpearmanCorrelationSimilarity PearsonCorrelationSimilarity]
           [org.apache.mahout.cf.taste.impl.neighborhood NearestNUserNeighborhood]
           [org.apache.mahout.cf.taste.impl.recommender GenericBooleanPrefUserBasedRecommender GenericItemBasedRecommender GenericUserBasedRecommender]
           [org.apache.mahout.cf.taste.recommender Recommender]
           [org.apache.mahout.cf.taste.impl.recommender.svd SVDRecommender ALSWRFactorizer]
           [org.apache.mahout.cf.taste.impl.eval GenericRecommenderIRStatsEvaluator AverageAbsoluteDifferenceRecommenderEvaluator]
           [org.apache.mahout.cf.taste.eval DataModelBuilder RecommenderBuilder]
           [java.io File]))

(defn line->item-tuple [line]
  (let [[id name] (s/split line #"\|")]
    [(Long/parseLong id) name]))

(defn items-lookup [path]
  (with-open [rdr (clojure.java.io/reader path)]
    (->> (line-seq rdr)
         (map line->item-tuple)
         (into {}))))

(defn to-names [recommended]
  (let [id->name (items-lookup "data/ml-100k/u.item")]
    (->> recommended
         (map #(.getItemID %))
         (map id->name))))

(defn user-based-recommender [file]
  (let [model         (FileDataModel. file)
        similarity    (PearsonCorrelationSimilarity. model)
        neighbourhood (NearestNUserNeighborhood. 100 similarity
                                                 model)]
    (GenericUserBasedRecommender. model
                                  neighbourhood
                                  similarity)))

(defn load-model [path]
  (-> (io/resource path)
      (io/file)
      (FileDataModel.)))

(defn user-recommender [model]
  (let [similarity   (PearsonCorrelationSimilarity. model)
        neighborhood (NearestNUserNeighborhood. 100 similarity
                                                model)]
    (GenericUserBasedRecommender. model neighborhood
                                  similarity)))

#_(defn build-user-recommender [model n]
  (let [similarity (PearsonCorrelationSimilarity. model)
        neighbourhood (NearestNUserNeighborhood. n similarity model)]
    (GenericUserBasedRecommender. model neighbourhood similarity)))

(defn pearson-recommender-builder [n]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [sim    (PearsonCorrelationSimilarity. model)
            nhood  (NearestNUserNeighborhood. n sim model)]
        (GenericUserBasedRecommender. model nhood sim)))))

(defn user-evaluator []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        evaluator (AverageAbsoluteDifferenceRecommenderEvaluator.)
        builder (reify RecommenderBuilder
                  (buildRecommender [this model]
                    (user-recommender model)))]
    (.evaluate evaluator builder nil model 0.7 0.3)))

(defn evaluate [model builder]
  (-> (AverageAbsoluteDifferenceRecommenderEvaluator.)
      (.evaluate builder nil model 0.7 0.3)))

(defn evaluate-ir [model builder]
  (-> (GenericRecommenderIRStatsEvaluator.)
      (.evaluate builder nil model nil 2
                 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD 1.0)
      (bean)))

#_(defn stats->map [stats n]
  (->> stats
       ((juxt #(.getPrecision %)
              #(.getRecall %)
              #(.getF1Measure %)
              #(.getFNMeasure % n)
              #(.getFallOut %)
              #(.getReach %)
              #(.getNormalizedDiscountedCumulativeGain %)))
       (zipmap [:precision
                :recall
                :F1
                :FN
                :fall-out
                :reach
                :NDCF])))

(defn run-user-ir-evaluation [path n]
  (let [model (FileDataModel. (File. path))
        evaluator (GenericRecommenderIRStatsEvaluator.)
        builder (pearson-recommender-builder n)
        stats (.evaluate evaluator builder nil model nil 2 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD 1.0)]
    (bean stats)))

;; Spearman

(defn spearman-recommender-builder [n]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [similarity   (SpearmanCorrelationSimilarity. model)
            neighborhood (NearestNUserNeighborhood. n similarity model)]
        (GenericUserBasedRecommender. model neighborhood similarity)))))

;; Boolean

(defn to-boolean-preferences [model]
  (-> (GenericBooleanPrefDataModel/toDataMap model)
      (GenericBooleanPrefDataModel.)))

(defn boolean-recommender-builder [n]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [sim   (TanimotoCoefficientSimilarity. model)
            nhood (NearestNUserNeighborhood. n sim model)]
        (GenericBooleanPrefUserBasedRecommender. model nhood sim)))))

;; Log likelihood

(defn boolean-loglikelihood-recommender-builder [n]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [sim (LogLikelihoodSimilarity. model)
            nhood (NearestNUserNeighborhood. n sim model)]
        (GenericBooleanPrefUserBasedRecommender. model nhood sim)))))

;; Anonymous recommendations

(defn with-preferences [model prefs]
  (let [pref-array (reduce (fn [prefs [index [item value]]]
                             (doto prefs
                               (.setItemID index item)
                               (.setValue index value)))
                           (GenericUserPreferenceArray. (count prefs))
                           (map-indexed vector prefs))]
    (doto (PlusAnonymousUserDataModel. model)
      (.setTempPrefs pref-array))))

(defn recommend-anon [recommender n]
  (let [anon PlusAnonymousUserDataModel/TEMP_USER_ID]
    (.recommend recommender anon n)))

(defn item-recommender [model]
  (let [sim (PearsonCorrelationSimilarity. model)]
    (GenericItemBasedRecommender. model sim)))


;; Model builder

(defn boolean-model-builder []
  (reify DataModelBuilder
    (buildDataModel [this data]
      (to-boolean-preferences data))))

(defn evaluate-ir-2 [model recommender-builder model-builder]
  (-> (GenericRecommenderIRStatsEvaluator.)
      (.evaluate recommender-builder model-builder model nil 2
                 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD 1.0)
      (bean)))


;; Slope one

(defn slope-one-recommender [model]
  (let []

  
   (reify Recommender
     (estimatePreference [this uid iid])
     (getDataModel [this] model)
     (recommend [this uid howmany]
       )
     (recommend [this uid howmany rescorer]
       )
     (removePreference [this uid iid])
     (setPreference [this uid iid value]))))




(defn build-loglikelihood-user-recommender [model]
  (let [similarity (LogLikelihoodSimilarity. model)
        neighbourhood (NearestNUserNeighborhood. 100 similarity model)]
    (GenericUserBasedRecommender. model neighbourhood similarity)))

(defn build-bool-user-recommender [model]
  (let [similarity (LogLikelihoodSimilarity. model)
        neighbourhood (NearestNUserNeighborhood. 100 similarity model)]
    (GenericBooleanPrefUserBasedRecommender. model neighbourhood similarity)))

(defn user-based []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        recommender (user-recommender model)]
    (println (.recommend recommender 1 1))))

(defn user-evaluator []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        evaluator (AverageAbsoluteDifferenceRecommenderEvaluator.)
        builder (reify RecommenderBuilder
                  (buildRecommender [this model]
                    (user-recommender model)))]
    (.evaluate evaluator builder nil model 0.7 0.3)))

(defn user-based []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        similarity (SpearmanCorrelationSimilarity. model)
        neighbourhood (NearestNUserNeighborhood. 100 similarity model)
        recommender (GenericUserBasedRecommender. model neighbourhood similarity)]
    (println (.recommend recommender 1 1))))

(defn item-based []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        similarity (SpearmanCorrelationSimilarity. model)
        recommender (GenericItemBasedRecommender. model similarity)]
    (println (.recommend recommender 1 1))))

(defn svd-recommender []
  (let [model (GroupLensDataModel. (File. "resources/ratings.dat"))
        factorizer (ALSWRFactorizer. model 100 0.01 100)
        recommender (SVDRecommender. model factorizer)]
    (println (.recommend recommender 1 1))))


(defn boolean-pref []
  (let [model (GenericBooleanPrefDataModel. (GenericBooleanPrefDataModel/toDataMap (FileDataModel. (File. "resources/ua.base"))))
        evaluator (GenericRecommenderIRStatsEvaluator.)
        recommender-builder (reify RecommenderBuilder
                              (buildRecommender [this model]
                                (build-bool-user-recommender model)))
        model-builder (reify DataModelBuilder
                        (buildDataModel [this data]
                          (GenericBooleanPrefDataModel.
                           (GenericBooleanPrefDataModel/toDataMap data))))
        stats (.evaluate evaluator recommender-builder model-builder model nil 10 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD 1.0)]
    {:precision (.getPrecision stats)
     :recall (.getRecall stats)}))

(defn slope-one-recommender [model]
  (reify Recommender
    (estimatePreference [this uid iid])
    (getDataModel [this] model)
    (recommend [this uid howmany]
     )
    (recommend [this uid howmany rescorer]
     )
    (removePreference [this uid iid])
    (setPreference [this uid iid value])))

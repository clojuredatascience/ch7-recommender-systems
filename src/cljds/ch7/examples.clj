(ns cljds.ch7.examples
  (:require [cljds.ch7.bloom-filter :refer [bloom-filter bloom-assoc bloom-contains?]]
            [cljds.ch7.data :refer :all]
            [cljds.ch7.mahout :refer [user-based-recommender user-recommender load-model to-boolean-preferences]]
            [cljds.ch7.minhash :refer [minhasher locality-sensitive-hash lsh-candidates lsh-assoc]]
            [cljds.ch7.slope-one :refer :all]
            [cljds.ch7.sparkling :refer [alternating-least-squares count-ratings parse-ratings training-ratings test-ratings rmse] :as sparkling]
            [incanter.svg :as svg]
            [clojure.java.io :as io]
            [incanter.charts :as c]
            [incanter.core :as i]
            [incanter.datasets :as d]
            [incanter.stats :as s]
            [incanter.svg :as svg]
            [sparkling.core :as spark]
            [sparkling.conf :as conf])
  (:import [org.apache.mahout.cf.taste.eval DataModelBuilder RecommenderBuilder]
           [org.apache.mahout.cf.taste.impl.eval RMSRecommenderEvaluator GenericRecommenderIRStatsEvaluator AverageAbsoluteDifferenceRecommenderEvaluator]
           [org.apache.mahout.cf.taste.impl.neighborhood NearestNUserNeighborhood]
           [org.apache.mahout.cf.taste.impl.recommender GenericBooleanPrefUserBasedRecommender GenericItemBasedRecommender GenericUserBasedRecommender]
           [org.apache.mahout.cf.taste.impl.similarity EuclideanDistanceSimilarity TanimotoCoefficientSimilarity UncenteredCosineSimilarity LogLikelihoodSimilarity SpearmanCorrelationSimilarity PearsonCorrelationSimilarity]))

(defn ex-7-1 []
  (->> (io/resource "ua.base")
       (io/reader)
       (line-seq)
       (first)))

(defn ex-7-2 []
  (->> (io/resource "u.item")
       (io/reader)
       (line-seq)
       (first)))

(defn ex-7-3 []
  (->> (load-ratings "ua.base")
       (first)))

(defn ex-7-4 []
  (-> (load-items "u.item")
      (get 1)))

;; Slope 1

(defn ex-7-5 []
  (->> (load-ratings "ua.base")
       (group-by :user)
       (vals)
       (item-differences)
       (first)))

(defn ex-7-6 []
  (let [diffs (->> (load-ratings "ua.base")
                   (group-by :user)
                   (vals)
                   (item-differences))]
    (println "893:343" (get-in diffs [893 343]))
    (println "343:893" (get-in diffs [343 893]))))

(defn ex-7-7 []
  (let [recommender (->> (load-ratings "ua.base")
                         (group-by :user)
                         (vals)
                         (slope-one-recommender))]
    (get-in recommender [893 343])))

(defn ex-7-8 []
  (let [user-ratings (->> (load-ratings "ua.base")
                          (group-by :user)
                          (vals))
        user-1       (first user-ratings)
        recommender  (->> (rest user-ratings)
                          (slope-one-recommender))
        items     (load-items "u.item")
        item-name (fn [item]
                    (get items (:item item)))]
    (->> (slope-one-recommend recommender user-1 10)
         (map item-name))))

;; Mahout

(defn ex-7-9 []
  (let [model        (load-model "ua.base")
        similarity   (EuclideanDistanceSimilarity. model)
        neighborhood (NearestNUserNeighborhood. 10 similarity
                                                model)
        recommender  (GenericUserBasedRecommender. model
                                                   neighborhood
                                                   similarity)
        items     (load-items "u.item")
        item-name (fn [id] (get items id))]
    (->> (.recommend recommender 1 5)
         (map #(item-name (.getItemID %))))))


(defn recommender-builder [n sim]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [nhood (NearestNUserNeighborhood. n sim model)]
        (GenericUserBasedRecommender. model nhood sim)))))

(defn evaluate-rmse [builder model]
  (-> (RMSRecommenderEvaluator.)
      (.evaluate builder nil model 0.7 1.0)))


(defn ex-7-10 []
  (let [model   (load-model "ua.base")
        builder (recommender-builder
                 10 (EuclideanDistanceSimilarity. model))]
    (evaluate-rmse builder model)))

(defn ex-7-11 []
  (let [model   (load-model "ua.base")
        builder (recommender-builder
                 10 (PearsonCorrelationSimilarity. model))]
    (evaluate-rmse builder model)))

(defn ex-7-12 []
  (let [model   (load-model "ua.base")
        builder (recommender-builder
                 10 (SpearmanCorrelationSimilarity. model))]
    (-> (RMSRecommenderEvaluator.)
        (.evaluate builder nil model 0.9 0.1))))

(defn ex-7-13 []
  (let [model (load-model "ua.base")
        sim   (EuclideanDistanceSimilarity. model)
        ns    (range 1 10)
        stats (for [n ns]
                (let [builder (recommender-builder n sim)]
                  (do (println n)
                      (evaluate-rmse builder model))))]
    (-> (c/scatter-plot ns stats
                        :x-label "Neighborhood size"
                        :y-label "RMSE")
        (i/view))))


(defn evaluate-ir [builder model]
  (-> (GenericRecommenderIRStatsEvaluator.)
      (.evaluate builder nil model nil 5
                 GenericRecommenderIRStatsEvaluator/CHOOSE_THRESHOLD
                 1.0)
      (bean)))

(defn ex-7-14 []
  (let [model   (load-model "ua.base")
        builder (recommender-builder
                 10 (EuclideanDistanceSimilarity. model))]
    (evaluate-ir builder model)))

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

(defn ex-7-15 []
  (let [model   (load-model "ua.base")
        sim     (EuclideanDistanceSimilarity. model)
        xs      (range 1 10)
        stats   (for [n xs]
                  (let [builder (recommender-builder n sim)]
                    (do (println n)
                        (evaluate-ir builder model))))]
    (plot-ir xs stats)))


(defn boolean-recommender-builder [n sim]
  (reify RecommenderBuilder
    (buildRecommender [this model]
      (let [nhood (NearestNUserNeighborhood. n sim model)]
        (GenericBooleanPrefUserBasedRecommender.
         model nhood sim)))))

(defn ex-7-16 []
  (let [model   (to-boolean-preferences (load-model "ua.base"))
        sim     (TanimotoCoefficientSimilarity. model)
        xs      (range 1 10)
        stats   (for [n xs]
                  (let [builder
                        (boolean-recommender-builder n sim)]
                    (do (println n)
                        (evaluate-ir builder model))))]
    (plot-ir xs stats)))

;; Bloom Filter

(defn ex-7-17 []
  (bloom-filter 8 5))

(defn ex-7-18 []
  (-> (bloom-filter 8 5)
      (bloom-assoc "Indiana Jones")
      (:filter)))

(defn ex-7-19 []
  (-> (bloom-filter 8 5)
      (bloom-assoc "Indiana Jones")
      (bloom-contains? "Indiana Jones")))

;; true

(defn ex-7-20 []
  (-> (bloom-filter 8 5)
      (bloom-assoc "Indiana Jones")
      (bloom-contains? "The Fugitive")))

;; false

(defn ex-7-21 []
  (-> (bloom-filter 8 5)
      (bloom-assoc "Indiana Jones")
      (bloom-contains? "Bogus (1996)")))

;; Minhash

(defn rated-items [user-ratings id]
  (->> (get user-ratings id)
       (map :item)))

(defn ex-7-22 []
  (let [ratings      (load-ratings "ua.base")
        user-ratings (group-by :user ratings)
        user-a       (rated-items user-ratings 405)
        user-b       (rated-items user-ratings 655)]
    (println "User 405:" (count user-a))
    (println "User 655:" (count user-b))
    (s/jaccard-index (set user-a) (set user-b))))

(defn ex-7-23 []
  (let [ratings      (load-ratings "ua.base")
        user-ratings (group-by :user ratings)
        minhash (minhasher 10)
        user-a  (minhash (rated-items user-ratings 405))
        user-b  (minhash (rated-items user-ratings 655))]
    (println "User 405:" user-a)
    (println "User 655:" user-b)
    (s/jaccard-index (set user-a) (set user-b))))


;; Locality Sensitive Hashing

(defn ex-7-24 []
  (let [ratings (load-ratings "ua.base")
        user-ratings (group-by :user ratings)
        minhash (minhasher 27)
        user-a  (minhash (rated-items user-ratings 13))
        lsh     (locality-sensitive-hash 3)]
    (lsh-assoc lsh {:id 13 :signature user-a})))

(defn ex-7-25 []
  (let [ratings (load-ratings "ua.base")
        user-ratings (group-by :user ratings)
        minhash (minhasher 27)
        user-a  (minhash (rated-items user-ratings 13))
        user-b  (minhash (rated-items user-ratings 655))]
    (-> (locality-sensitive-hash 3)
        (lsh-assoc {:id 13  :signature user-a})
        (lsh-assoc {:id 655 :signature user-b}))))

(defn ex-7-26 []
  (let [ratings (load-ratings "ua.base")
        user-ratings (group-by :user ratings)
        minhash   (minhasher 27)
        user-b    (minhash (rated-items user-ratings 655))
        user-c    (minhash (rated-items user-ratings 405))
        user-a    (minhash (rated-items user-ratings 13))]
    (-> (locality-sensitive-hash 3)
        (lsh-assoc {:id 655 :signature user-b})
        (lsh-assoc {:id 405 :signature user-c})
        (lsh-candidates user-a))))


;; PCA

(defn ex-7-27 []
  (i/view (d/get-dataset :iris)))

(defn plot-iris-columns [a b]
  (let [data (->> (d/get-dataset :iris)
                  (i/$ [a b])
                  (i/to-matrix))]
    (-> (c/scatter-plot (i/$ (range 50) 0 data)
                        (i/$ (range 50) 1 data)
                        :x-label (name a)
                        :y-label (name b))
        (c/add-points (i/$ (range 50 100) 0 data)
                      (i/$ (range 50 100) 1 data))
        (c/add-points (i/$ [:not (range 100)] 0 data)
                      (i/$ [:not (range 100)] 1 data))
        (i/view))))

(defn ex-7-28 []
  (plot-iris-columns :Sepal.Width
                     :Sepal.Length))

(defn ex-7-29 []
  (plot-iris-columns :Petal.Width
                     :Petal.Length))

(defn ex-7-30 []
  (let [data (->> (d/get-dataset :iris)
                  (i/$ (range 4))
                  (i/to-matrix))
        components (s/principal-components data)
        pc1 (i/$ 0 (:rotation components))
        pc2 (i/$ 1 (:rotation components))
        xs (i/mmult data pc1)
        ys (i/mmult data pc2)]
    (-> (c/scatter-plot (i/$ (range 50) 0 xs)
                        (i/$ (range 50) 0 ys)
                        :x-label "Principle Component 1"
                        :y-label "Principle Component 2")
        (c/add-points (i/$ (range 50 100) 0 xs)
                      (i/$ (range 50 100) 0 ys))
        (c/add-points (i/$ [:not (range 100)] 0 xs)
                      (i/$ [:not (range 100)] 0 ys))
        (i/view))))

(defn project-into [matrix d]
  (let [svd (i/decomp-svd matrix)]
    {:U (i/$  (range d) (:U svd))
     :S (i/diag (take d (:S svd)))
     :V (i/trans
         (i/$ (range d) (:V svd)))}))

(defn ex-7-31 []
  (let [matrix (s/sample-mvn 100
                             :sigma (i/matrix [[1 0.8]
                                               [0.8 1]]))]
    (println "Original" matrix)
    (project-into matrix 1)))

(defn ex-7-32 []
  (let [matrix (s/sample-mvn 100
                             :sigma (i/matrix [[1 0.8]
                                               [0.8 1]]))
        svd (project-into matrix 1)
        projection (i/mmult (:U svd)
                            (:S svd)
                            (:V svd))]
    (-> (c/scatter-plot (i/$ 0 matrix) (i/$ 1 matrix)
                        :x-label "x"
                        :y-label "y"
                        :series-label "Original"
                        :legend true)
        (c/add-points (i/$ 0 projection) (i/$ 1 projection)
                      :series-label "Projection")
        (i/view))))

(defn ex-7-33 []
  (let [svd (->> (d/get-dataset :iris)
                 (i/$ (range 4))
                 (i/to-matrix)
                 (i/decomp-svd))
        dims 2
        u (i/$     (range dims) (:U svd))
        s (i/diag  (take dims   (:S svd)))
        v (i/trans (i/$ (range dims) (:V svd)))
        projection (i/mmult u s v)]
    (-> (c/scatter-plot (i/$ (range 50) 0 projection)
                        (i/$ (range 50) 1 projection)
                        :x-label "Dimension 1"
                        :y-label "Dimension 2")
        (c/add-points (i/$ (range 50 100) 0 projection)
                      (i/$ (range 50 100) 1 projection))
        (c/add-points (i/$ [:not (range 100)] 0 projection)
                      (i/$ [:not (range 100)] 1 projection))
        (i/view))))


;; Spark

(defn ex-7-34 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (count-ratings sc)))


(defn ex-7-35 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (->> (parse-ratings sc)
         (spark/collect)
         (first))))

(defn ex-7-36 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))    
    (let [ratings (spark/cache (parse-ratings sc))
          train (training-ratings ratings)
          test  (test-ratings ratings)]
      (println "Training:" (spark/count train))
      (println "Test:"     (spark/count test)))))

(defn ex-7-37 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (-> (parse-ratings sc)
        (training-ratings)
        (alternating-least-squares {:rank 10
                                    :num-iter 10
                                    :lambda 1.0}))))

(defn ex-7-38 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (let [options {:rank 10
                   :num-iter 10
                   :lambda 1.0}
          model (-> (parse-ratings sc)
                    (training-ratings )
                    (alternating-least-squares options))]
      (into [] (.recommendProducts model 1 3)))))

(defn ex-7-39 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (let [items   (load-items "u.item")
          id->name (fn [id] (get items id))
          options {:rank 10
                   :num-iter 10
                   :lambda 1.0}
          model (-> (parse-ratings sc)
                    (training-ratings )
                    (alternating-least-squares options))]
      (->> (.recommendProducts model 1 3)
           (map (comp id->name #(.product %)))))))


(defn ex-7-40 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (let [options {:num-iter 10 :lambda 0.1}
          training (-> (parse-ratings sc)
                       (training-ratings)
                       (spark/cache)) 
          ranks    (range 2 50 2)
          errors   (for [rank ranks]
                     (doto (-> (alternating-least-squares training
                                  (assoc options :rank rank))
                               (rmse training))
                       (println "RMSE for rank" rank)))]
      (-> (c/scatter-plot ranks errors
                          :x-label "Rank"
                          :y-label "RMSE")
          (i/view)))))

(defn ex-7-41 []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local")
                             (conf/app-name "ch7"))
    (let [options {:num-iter 10 :lambda 0.1}
          parsed   (spark/cache (parse-ratings sc))
          training (spark/cache (training-ratings parsed))
          test     (spark/cache (test-ratings parsed))
          ranks    (range 2 50 2)
          errors   (for [rank ranks]
                     (doto (-> (alternating-least-squares training
                                  (assoc options :rank rank))
                               (rmse test))
                       (println "RMSE for rank" rank)))]
      (-> (c/scatter-plot ranks errors
                          :x-label "Rank"
                          :y-label "RMSE")
          (svg/save-svg "/tmp/7-355.svg")))))

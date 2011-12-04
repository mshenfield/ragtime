(ns ragtime.test.core
  (:use clojure.test
        ragtime.core
        ragtime.database)
  (:require [ragtime.strategy :as strategy]))

(defmigration test-migrate-1)

(defmigration test-migrate-2
  {:up   (fn [_] (println "up"))
   :down (fn [_] (println "down"))})

(deftest test-defmigration
  (testing "id based on symbol"
    (is (= (:id test-migrate-1)
           "ragtime.test.core/test-migrate-1")))
  (testing "migrations listed in order"
    (is (= (list-migrations 'ragtime.test.core)
           [test-migrate-1
            test-migrate-2])))
  (testing "retains up and down keys"
    (is (:up test-migrate-2))
    (is (:down test-migrate-2))))

(defrecord InMemoryDB [data]
  Database
  (add-migration-id [_ id]
    (swap! data update-in [:migrations] conj id))
  (remove-migration-id [_ id]
    (swap! data update-in [:migrations] disj id))
  (applied-migration-ids [_]
    (seq (:migrations @data))))

(defn in-memory-db []
  (InMemoryDB. (atom {:migrations #{}})))

(deftest test-migrate-and-rollback
  (let [database (in-memory-db)
        migration {:id "m"
                   :up   (fn [db] (swap! (:data db) assoc :x 1))
                   :down (fn [db] (swap! (:data db) dissoc :x))}]
    (testing "migrate"
      (migrate database migration)
      (is (= (:x @(:data database)) 1))
      (is (contains? (set (applied-migrations database)) migration)))
    (testing "rollback"
      (rollback database migration)
      (is (nil? (:x @(:data database))))
      (is (not (contains? (set (applied-migrations database)) migration))))))

(deftest test-migrate-all
  (let [database (in-memory-db)
        assoc-x {:id "assoc-x"
                 :up   (fn [db] (swap! (:data db) assoc :x 1))
                 :down (fn [db] (swap! (:data db) dissoc :x))}
        assoc-y {:id "assoc-y"
                 :up   (fn [db] (swap! (:data db) assoc :y 2))
                 :down (fn [db] (swap! (:data db) dissoc :y))}
        assoc-z {:id "assoc-z"
                 :up   (fn [db] (swap! (:data db) assoc :z 3))
                 :down (fn [db] (swap! (:data db) dissoc :z))}]
    (migrate-all database [assoc-x assoc-y])
    (is (= (:x @(:data database)) 1))
    (is (= (:y @(:data database)) 2))
    (migrate-all database [assoc-x assoc-z] strategy/rebase)
    (is (= (:x @(:data database)) 1))
    (is (nil? (:y @(:data database))))
    (is (= (:z @(:data database)) 3))))
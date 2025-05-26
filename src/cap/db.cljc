(ns cap.db
  (:require [babashka.pods :as pods]
            [cheshire.core :as json]
            [clojure.string :as str]
            #?@(:bb []
                :clj [[honey.sql :as sql]
                      [honey.sql.helpers :as helpers]]))
  (:import (java.time LocalDateTime ZonedDateTime ZoneId)
           (java.time.format DateTimeFormatter)))

(pods/load-pod 'org.babashka/go-sqlite3 "0.2.7")
(require '[pod.babashka.go-sqlite3 :as sqlite])

#?(:bb (do (require '[babashka.deps :as deps])
           (deps/add-deps '{:deps {com.github.seancorfield/honeysql {:mvn/version "2.7.1310"}}})
           (require '[honey.sql :as sql])
           (require '[honey.sql.helpers :as helpers])))

(defn table-info
  [database table-name]
  (first (sqlite/query database ["select * from sqlite_schema where type = ? and name = ?" "table" table-name])))

(defn has-table?
  [database table-name]
  (boolean (table-info database table-name)))

(defn init
  "Update database to the latest version, creating it if it doesn't exist."
  [config]
  (let [{:db/keys [location]} config]
    (sqlite/query location ["PRAGMA journal_mode=WAL"])
    (when-not (has-table? location "captures")
      (println "Adding captures table")
      (sqlite/query location ["create table captures (id integer primary key, name text, directory text, session text, attributes text, created_at text default current_timestamp)"]))
    (when-not (has-table? location "lines")
      (println "Adding lines table")
      (sqlite/query location ["create table lines (id integer primary key, capture_id integer, line text)"]))
    ; (when-not (str/includes? (:sql (table-info location "captures")) "created_at")
    ;   (println "Adding column created_at to captures")
    ;   (sqlite/query location ["alter table captures add column created_at text default datetime('now')"])
    ;   (sqlite/execute! location ["update captures set created_at = datetime('now')"]))
    ))

(defn add-capture-query
  [name directory session attributes]
  (sql/format
    {:insert-into :captures
     :columns [:name :directory :session :attributes]
     :values [[name directory session (json/generate-string attributes)]]}))

(defn add-capture
  [{:db/keys [location]} name directory session attributes]
  (let [{:keys [last-inserted-id]} (sqlite/execute! location (add-capture-query name directory session attributes))]
    {:id last-inserted-id}))

(defn add-line-query
  [capture-id line]
  (sql/format
    {:insert-into :lines
     :columns [:capture_id :line]
     :values [[capture-id line]]}))

(defn add-line
  [{:db/keys [location]} capture line]
  (let [{:keys [last-inserted-id]} (sqlite/execute! location (add-line-query (:id capture) line))]
    {:id last-inserted-id}))

(defn find-captures-query
  [{:keys [name directory session]} {:keys [limit]}]
  (cond-> {:select :*
           :from :captures
           :order-by [[:created_at :desc]]}
    name (helpers/where [:= :name name])
    directory (helpers/where [:= :directory directory])
    session (helpers/where [:= :session session])
    limit (helpers/limit limit)
    :finally (sql/format)))

(def formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn parse-sqlite-date
  [sqlite-date]
  (-> sqlite-date
      (LocalDateTime/parse formatter)
      (ZonedDateTime/of (ZoneId/of "UTC"))
      (.withZoneSameInstant (ZoneId/systemDefault))))

(defn decode-captures
  [{:keys [created_at] :as db-capture}]
  (-> db-capture
      (update :attributes json/parse-string true)
      (dissoc :created_at)
      (assoc :created-at (parse-sqlite-date created_at))))

(defn find-captures
  [{:db/keys [location]} args opts]
  (->> (sqlite/query location (find-captures-query args opts))
       (mapv decode-captures)))

(defn get-capture-query
  [id]
  (sql/format {:select :*
               :from :captures
               :where [:= :id id]}))

(defn get-capture
  [{:db/keys [location]} id]
  (->> (sqlite/query location (get-capture-query id))
       (mapv decode-captures)
       first))

(defn find-lines-query
  [id {:keys [limit]}]
  (cond-> {:select :*
           :from :lines
           :where [:= :capture_id id]
           :order-by [:id]}
    limit (helpers/limit limit)
    :finally (sql/format)))

(defn get-lines
  [{:db/keys [location]} capture opts]
  (->> (sqlite/query location (find-lines-query (:id capture) opts))
       ; (mapv decode-lines)
       ))



(comment
  (sqlite/execute! "foo.db" ["create table foo (bar)"])
  (sqlite/execute! "foo.db" ["insert into foo (bar) values (?)" "bar"])
  (sqlite/query    "foo.db" ["select * from foo"])
  (sqlite/query    "foo.db" ["PRAGMA journal_mode=WAL"])
  (sqlite/query    "foo.db" ["select * from sqlite_schema where type = 'table' and name = 'fo'"])

  (sqlite/query "cap.db" ["pragma foreign_keys = on"])
  (sqlite/query "cap.db" ["pragma foreign_keys"])
  (sqlite/execute! "cap.db" ["insert into captures (name) values (?)" "second"])
  (sqlite/execute! "cap.db" ["insert into attributes (capture_id) values (?)" 1])
  (sqlite/execute! "cap.db" ["delete from captures where id = ?" 1])
  (sqlite/execute! "cap.db" ["insert into attributes values (3, 4)"])

  (init {:db/location "cap.db"})
  (add-capture-query "Generated" "/home/nate" "0f1b2823-efa4-4b4a-9e38-64b3ac5f5632" {})
  (add-capture {:db/location "cap.db"} "Generated" "/home/nate" "0f1b2823-efa4-4b4a-9e38-64b3ac5f5632" {})

  (find-captures-query {:directory "/home/nate" :session "foobarsession"} {:limit 2})
  (find-captures {:db/location "cap.db"} {:directory "/home/nate/projects/bb-scripts"} {:limit 2})

  (add-line-query 2 "test log line")
  (add-line {:db/location "cap.db"} {:id 2} "test log line")

  (find-lines-query 2 nil)
  (get-lines {:db/location "cap.db"} {:id 2} nil)

  (get-capture-query 14)
  (get-capture {:db/location "cap.db"} "14")

  (table-info "cap.db" "captures")

  (sqlite/execute! "foo.db" ["create table foo (bar)"])
  )


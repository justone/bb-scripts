(ns cap-db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [cap.db :as db])
  (:import (java.time ZonedDateTime)))

;; ============================================================================
;; LEVEL 1: PURE FUNCTION TESTS (Query Construction)
;; Testing SQL generation functions without database
;; ============================================================================

(deftest add-capture-query-test
  (testing "basic capture insertion query"
    (let [query (db/add-capture-query "test-capture" "/home/user" "session-123" {})
          [sql & params] query]
      (is (vector? query))
      (is (= "INSERT INTO captures (name, directory, session, attributes) VALUES (?, ?, ?, ?)" sql))
      (is (= ["test-capture" "/home/user" "session-123" "{}"] params))))

  (testing "capture with complex attributes"
    (let [query (db/add-capture-query "complex" "/path" "sess" {:key "value" :num 42})
          [sql name directory session attributes] query]
      (is (= "complex" name))
      (is (= "{\"key\":\"value\",\"num\":42}" attributes)))))

(deftest add-line-query-test
  (testing "basic line insertion query"
    (let [query (db/add-line-query 123 "test log line")
          [sql & params] query]
      (is (vector? query))
      (is (= "INSERT INTO lines (capture_id, line) VALUES (?, ?)" sql))
      (is (= [123 "test log line"] params))))

  (testing "line with special characters"
    (let [query (db/add-line-query 456 "line with \"quotes\" and 'apostrophes'")
          [sql capture-id line] query]
      (is (= 456 capture-id))
      (is (= "line with \"quotes\" and 'apostrophes'" line)))))

(deftest find-captures-query-test
  (testing "query with no filters"
    (let [query (db/find-captures-query {} {})
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id ORDER BY created_at DESC" sql))))

  (testing "query with name filter"
    (let [query (db/find-captures-query {:name "test-name"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE name = ? ORDER BY created_at DESC" sql))
      (is (= ["test-name"] params))))

  (testing "query with directory filter"
    (let [query (db/find-captures-query {:directory "/home/user"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE directory = ? ORDER BY created_at DESC" sql))
      (is (= ["/home/user"] params))))

  (testing "query with session filter"
    (let [query (db/find-captures-query {:session "session-123"} {})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE session = ? ORDER BY created_at DESC" sql))
      (is (= ["session-123"] params))))

  (testing "query with limit"
    (let [query (db/find-captures-query {} {:limit 10})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id ORDER BY created_at DESC LIMIT ?" sql))
      (is (= [10] params))))

  (testing "query with multiple filters and limit"
    (let [query (db/find-captures-query {:name "test" :directory "/path"} {:limit 5})
          [sql & params] query]
      (is (= "SELECT * FROM captures AS c INNER JOIN (SELECT capture_id, COUNT(id) AS line_count FROM lines GROUP BY capture_id) AS l ON c.id = l.capture_id WHERE (name = ?) AND (directory = ?) ORDER BY created_at DESC LIMIT ?" sql))
      (is (= ["test" "/path" 5] params)))))

(deftest get-capture-query-test
  (testing "basic get capture by id"
    (let [query (db/get-capture-query 42)
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM captures WHERE id = ?" sql))
      (is (= [42] params))))

  (testing "get capture with string id"
    (let [query (db/get-capture-query "123")
          [sql & params] query]
      (is (= ["123"] params)))))

(deftest set-comment-query-test
  (testing "basic comment update"
    (let [query (db/set-comment-query 42 "test comment")
          [sql & params] query]
      (is (vector? query))
      (is (= "UPDATE captures SET comment = ? WHERE id = ?" sql))
      (is (= ["test comment" 42] params))))

  (testing "comment with special characters"
    (let [query (db/set-comment-query 123 "comment with 'quotes' and newlines\n")
          [sql & params] query]
      (is (= ["comment with 'quotes' and newlines\n" 123] params)))))

(deftest find-lines-query-test
  (testing "basic lines query"
    (let [query (db/find-lines-query 42 {})
          [sql & params] query]
      (is (vector? query))
      (is (= "SELECT * FROM lines WHERE capture_id = ? ORDER BY id DESC" sql))
      (is (= [42] params))))

  (testing "lines query with limit"
    (let [query (db/find-lines-query 123 {:limit 50})
          [sql & params] query]
      (is (= "SELECT * FROM lines WHERE capture_id = ? ORDER BY id DESC LIMIT ?" sql))
      (is (= [123 50] params)))))

;; ============================================================================
;; SQL Structure Validation Tests
;; ============================================================================

(deftest query-structure-validation
  (testing "all query functions return proper structure"
    (let [queries [(db/add-capture-query "test" "/path" "sess" {})
                   (db/add-line-query 1 "line")
                   (db/find-captures-query {} {})
                   (db/get-capture-query 1)
                   (db/set-comment-query 1 "comment")
                   (db/find-lines-query 1 {})]]
      (doseq [query queries]
        (let [[sql & params] query]
          (is (vector? query) "Query should be a vector")
          (is (string? sql) "First element should be SQL string")
          (is (not-empty sql) "SQL string should not be empty"))))))

(deftest sql-injection-safety
  (testing "parameters are properly bound, not interpolated"
    ;; Test that potentially dangerous strings don't appear in SQL
    (let [dangerous-input "'; DROP TABLE captures; --"
          queries [(db/add-capture-query dangerous-input "/path" "sess" {})
                   (db/add-line-query 1 dangerous-input)
                   (db/find-captures-query {:name dangerous-input} {})
                   (db/set-comment-query 1 dangerous-input)]]
      (doseq [query queries]
        (let [[sql & params] query]
          (is (not (str/includes? sql "DROP TABLE"))
              "Dangerous SQL should not appear in generated query string"))))))

;; ============================================================================
;; LEVEL 2: IMPURE FUNCTION TESTS (Database Integration)
;; Testing database operations with real SQLite
;; ============================================================================

;; Test Database Fixture Infrastructure
(def ^:dynamic *test-db-config* nil)

(defn db-fixture
  "Clojure test fixture that provides a fresh database for each test"
  [test-fn]
  (let [temp-file (str (fs/create-temp-file {:suffix ".db"}))
        conn (db/connect temp-file)
        config {:db/location temp-file :db/conn conn}]
    (db/init config)
    (binding [*test-db-config* config]
      (try
        (test-fn)
        (finally
          (db/close conn)
          (fs/delete temp-file))))))

(use-fixtures :each db-fixture)

;; ============================================================================
;; CRUD Operations Tests
;; ============================================================================

(deftest init-test
  (testing "database initialization creates required tables"
    ;; Tables should already be created by db-fixture
    (is (db/has-table? (:db/conn *test-db-config*) "captures"))
    (is (db/has-table? (:db/conn *test-db-config*) "lines"))))

(deftest add-capture-integration-test
  (testing "add-capture creates record and returns ID"
    (let [result (db/add-capture *test-db-config* "test-capture" "/test/path" "session-123" {:key "value"})]
      (is (map? result))
      (is (integer? (:id result)))
      (is (pos? (:id result)))))

  (testing "add-capture with empty attributes"
    (let [result (db/add-capture *test-db-config* "empty-attrs" "/path" "sess" {})]
      (is (integer? (:id result))))))

(deftest add-line-integration-test
  (testing "add-line creates record linked to capture"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})
          line-result (db/add-line *test-db-config* capture "test log line")]
      (is (map? line-result))
      (is (integer? (:id line-result)))
      (is (pos? (:id line-result)))))

  (testing "add-line with special characters"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})
          special-line "Line with 'quotes' and \"double quotes\" and newlines\n"]
      (is (map? (db/add-line *test-db-config* capture special-line))))))

(deftest find-captures-integration-test
  (testing "find-captures returns empty list when no captures"
    (let [results (db/find-captures *test-db-config* {} {})]
      (is (vector? results))
      (is (empty? results))))

  (testing "find-captures returns captures with line counts"
    ;; Create capture with some lines
    (let [capture (db/add-capture *test-db-config* "test-capture" "/test/path" "session-123" {:key "value"})]
      (db/add-line *test-db-config* capture "line 1")
      (db/add-line *test-db-config* capture "line 2")
      (db/add-line *test-db-config* capture "line 3")

      (let [results (db/find-captures *test-db-config* {} {})]
        (is (= 1 (count results)))
        (let [found-capture (first results)]
          (is (= (:id capture) (:id found-capture)))
          (is (= "test-capture" (:name found-capture)))
          (is (= "/test/path" (:directory found-capture)))
          (is (= "session-123" (:session found-capture)))
          (is (= 3 (:line-count found-capture)))
          (is (instance? ZonedDateTime (:created-at found-capture)))
          (is (map? (:attributes found-capture)))
          (is (= "value" (get-in found-capture [:attributes :key])))))))

  (testing "find-captures filters by session"
    ;; Create captures in different sessions
    (let [capture-a (db/add-capture *test-db-config* "capture1" "/path" "session-a" {})
          capture-b (db/add-capture *test-db-config* "capture2" "/path" "session-b" {})]
      (db/add-line *test-db-config* capture-a "line 1")
      (db/add-line *test-db-config* capture-b "line 1"))

    (let [session-a-results (db/find-captures *test-db-config* {:session "session-a"} {})
          session-b-results (db/find-captures *test-db-config* {:session "session-b"} {})]
      (is (= 1 (count session-a-results)))
      (is (= 1 (count session-b-results)))
      (is (= "capture1" (:name (first session-a-results))))
      (is (= "capture2" (:name (first session-b-results))))))

  (testing "find-captures filters by directory"
    (let [capture-a (db/add-capture *test-db-config* "capture1" "/path/a" "sess" {})
          capture-b (db/add-capture *test-db-config* "capture2" "/path/b" "sess" {})]
      (db/add-line *test-db-config* capture-a "line 1")
      (db/add-line *test-db-config* capture-b "line 1"))

    (let [results (db/find-captures *test-db-config* {:directory "/path/a"} {})]
      (is (= 1 (count results)))
      (is (= "capture1" (:name (first results))))))

  (testing "find-captures respects limit"
    ;; Create multiple captures
    (dotimes [i 5]
      (let [capture (db/add-capture *test-db-config* (str "capture-" i) "/path" "sess" {})]
        (db/add-line *test-db-config* capture "dummy line"))) ; Need line for JOIN

    (let [limited-results (db/find-captures *test-db-config* {} {:limit 2})]
      (is (= 2 (count limited-results))))))

(deftest get-capture-integration-test
  (testing "get-capture returns specific capture by id"
    (let [created (db/add-capture *test-db-config* "test-capture" "/test/path" "session-123" {:key "value"})
          retrieved (db/get-capture *test-db-config* (:id created))]
      (is (map? retrieved))
      (is (= (:id created) (:id retrieved)))
      (is (= "test-capture" (:name retrieved)))
      (is (= "/test/path" (:directory retrieved)))
      (is (= "session-123" (:session retrieved)))
      (is (instance? ZonedDateTime (:created-at retrieved)))
      (is (= {:key "value"} (:attributes retrieved)))))

  (testing "get-capture returns nil for non-existent id"
    (is (nil? (db/get-capture *test-db-config* 999)))))

(deftest set-comment-integration-test
  (testing "set-comment updates capture comment"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})
          _ (db/set-comment *test-db-config* capture "test comment")
          updated (db/get-capture *test-db-config* (:id capture))]
      (is (= "test comment" (:comment updated)))))

  (testing "set-comment with special characters"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})
          special-comment "Comment with 'quotes' and newlines\nand unicode: 🚀"
          _ (db/set-comment *test-db-config* capture special-comment)
          updated (db/get-capture *test-db-config* (:id capture))]
      (is (= special-comment (:comment updated))))))

(deftest get-lines-integration-test
  (testing "get-lines returns lines for capture in correct order"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})]
      ;; Add lines in order
      (db/add-line *test-db-config* capture "first line")
      (db/add-line *test-db-config* capture "second line")
      (db/add-line *test-db-config* capture "third line")

      (let [lines (db/get-lines *test-db-config* capture {})]
        (is (= 3 (count lines)))
        ;; Should be in reverse order (newest first)
        (is (= "third line" (:line (first lines))))
        (is (= "second line" (:line (second lines))))
        (is (= "first line" (:line (nth lines 2))))
        ;; All should have capture_id
        (is (every? #(= (:id capture) (:capture_id %)) lines)))))

  (testing "get-lines respects limit"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})]
      (dotimes [i 10]
        (db/add-line *test-db-config* capture (str "line " i)))

      (let [limited-lines (db/get-lines *test-db-config* capture {:limit 3})]
        (is (= 3 (count limited-lines))))))

  (testing "get-lines returns empty for capture with no lines"
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})
          lines (db/get-lines *test-db-config* capture {})]
      (is (empty? lines)))))

;; ============================================================================
;; Data Transformation Tests
;; ============================================================================

(deftest date-parsing-test
  (testing "parse-sqlite-date converts SQLite timestamp to ZonedDateTime"
    (let [sqlite-date "2023-12-25 15:30:45"
          parsed (db/parse-sqlite-date sqlite-date)]
      (is (instance? ZonedDateTime parsed))
      (is (= 2023 (.getYear parsed)))
      (is (= 12 (.getMonthValue parsed)))
      (is (= 25 (.getDayOfMonth parsed)))
      ;; Hour might be different due to timezone conversion
      (is (integer? (.getHour parsed)))
      (is (= 30 (.getMinute parsed)))
      (is (= 45 (.getSecond parsed))))))

(deftest decode-captures-test
  (testing "decode-captures transforms database record to domain object"
    (let [db-record {:id 1
                     :name "test"
                     :directory "/path"
                     :session "sess"
                     :attributes "{\"key\":\"value\",\"num\":42}"
                     :created_at "2023-12-25 15:30:45"
                     :line_count 5
                     :comment "test comment"}
          decoded (db/decode-captures db-record)]
      (is (= 1 (:id decoded)))
      (is (= "test" (:name decoded)))
      (is (= "/path" (:directory decoded)))
      (is (= "sess" (:session decoded)))
      (is (= {:key "value" :num 42} (:attributes decoded)))
      (is (= 5 (:line-count decoded))) ; Renamed from line_count
      (is (= "test comment" (:comment decoded)))
      (is (instance? ZonedDateTime (:created-at decoded)))
      (is (not (contains? decoded :created_at))) ; Old key removed
      (is (not (contains? decoded :line_count))))) ; Old key removed

  (testing "decode-captures handles empty attributes"
    (let [db-record {:id 1 :name "test" :directory "/path" :session "sess"
                     :attributes "{}" :created_at "2023-12-25 15:30:45" :line_count 0}
          decoded (db/decode-captures db-record)]
      (is (= {} (:attributes decoded))))))

;; ============================================================================
;; Schema Migration Tests
;; ============================================================================

(deftest schema-migration-test
  (testing "init is idempotent - can be called multiple times safely"
    ;; Call init again on existing database
    (db/init *test-db-config*)
    (is (db/has-table? (:db/conn *test-db-config*) "captures"))
    (is (db/has-table? (:db/conn *test-db-config*) "lines"))

    ;; Should still be able to create captures
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})]
      (is (integer? (:id capture)))))

  (testing "comment column exists in captures table"
    ;; Verify comment column functionality works
    (let [capture (db/add-capture *test-db-config* "test" "/path" "sess" {})]
      (db/set-comment *test-db-config* capture "test comment")
      (let [retrieved (db/get-capture *test-db-config* (:id capture))]
        (is (= "test comment" (:comment retrieved)))))))

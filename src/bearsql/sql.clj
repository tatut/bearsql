(ns bearsql.sql
  "SQL builder."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn symbol->sql [sym]
  (-> sym str (str/replace #"-" "_")))

(def config {:symbol->sql symbol->sql
             :log-sql-level nil})

(defn set-config!
  "Set compile time configuration. This must be called
  before the [[bearsql.core/q]] macroexpansions happen to
  take effect."
  [key value]
  (alter-var-root #'config #(assoc % key value))
  :ok)

(declare build)

(defn- combine [sep parts]
  (reduce (fn [[combined-sql combined-params]
               [item-sql & item-params]]
            [(str combined-sql (when (some? combined-sql) sep)
                  item-sql)
             (into combined-params item-params)])
          [nil []]
          (map #(build [%]) parts)))

(defn- combine-as [items]
  (loop [out []
         items items]
    (if (empty? items)
      out
      (let [[item & items] items
            next (first items)]
        (if (= "AS" (some-> next str str/upper-case))
          (let [[alias & items] (rest items)]
            (recur (conj out [:as item alias])
                   items))
          (recur (conj out item) items))))))

(defn build
  "Build SQL. Returns [sql-string & parameters]."
  ([parts] (build parts false))
  ([parts toplevel?]
   (let [{:keys [symbol->sql log-sql-level]} config]
     (loop [sql nil
            params []
            parts (combine-as parts)]
       (if (empty? parts)
         (let [sql-and-params
               (into [(str/replace sql #"  " " ")] params)]
           (when (and toplevel? log-sql-level)
             (log/log log-sql-level sql-and-params))
           sql-and-params)
         (let [[p & parts] parts
               more-sql (fn [thing]
                          (str sql (when (some? sql) " ") thing))]
           (cond
             (symbol? p)
             (recur (more-sql (symbol->sql p)) params parts)

             (and (vector? p) (= :as (first p)))
             (let [[sql* params*] (combine " AS " (rest p))]
               (recur (more-sql sql*)
                      (into params params*)
                      parts))

             (vector? p)
             (let [[sql* params*] (combine ", " (combine-as p))]
               (recur (more-sql sql*)
                      (into params params*)
                      parts))

            ;; @form, pass the form into query parameters
             (and (seq? p) (= 'clojure.core/deref (first p)))
             (recur (more-sql "?")
                    (conj params (second p))
                    parts)

             (list? p)
             (let [[sql* & params*] (build p)]
               (recur (more-sql (str "(" sql* ")"))
                      (into params params*)
                      parts))

            ;; Any other Clojure value, pass as is into query parameters
             :else
             (recur (more-sql "?")
                    (conj params p)
                    parts))))))))

(ns bearsql.core
  "Bear SQL. A bare words SQL library."
  (:require [next.jdbc :as jdbc]
            [bearsql.sql :as sql]))

(def ^:dynamic *db*
  "The ambient next.jdbc connectable that is used if not explicitly specified.")

(defmacro q
  "Run a bare words SQL query.
  The first argument can be an options map that specifies the
  database to use with :db and other options to pass to next.jdbc.

  The is built from the rest of the arguments as follows:
  - any symbol is stringified
  - a vector is turned into a comma separated list
  - a list is recursively converted into SQL and surrounded with parenthesis
  - @form is a Clojure value that is passed in as a query parameter
  - any other value (string, number, so on) is passed in as query parameter

  Symbols are converted to SQL keywords by replacing dashes with underscores.
  This can be changed by passing in the :symbol->sql function or binding
  bearsql.core/*symbol->sql*.

  Inside a comma separated list, the keyword AS (case insensitive) is
  special and combines 3 values as one (eg. foo as bar).

  examples:
  (q select * from items where id = @some-id)

  (q select [it.name as item, cat.name as category]
     from item it join category cat on it.category-id=cat.id)

  "
  [& opts-and-query]
  (let [[opts query] (if (map? (first opts-and-query))
                       [(first opts-and-query) (rest opts-and-query)]
                       [nil opts-and-query])]
    `(next.jdbc/execute!
      ~(if (contains? opts :db)
         (:db opts)
         `*db*)
      ~(sql/build query true))))


(defmacro q1
  "Same as [[q]] but returns a single value as is.

  Example:
  (= 1 (q1 select id from items where id = 1))

  "
  [& args]
  `(-> (q ~@args) first vals first))

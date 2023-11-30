(ns bearsql.core-test
  "Basic test suite for bearsql"
  (:require [next.jdbc :as jdbc]
            [bearsql.core :refer [q q1]]
            [clojure.test :refer [use-fixtures testing deftest is]])
  (:import (java.sql DriverManager)))

(bearsql.sql/set-config! :log-sql-level :info)

(def ddl
  ["drop schema public cascade"
   "create table category (
      id integer identity primary key,
      name varchar(255))"

   "create table manufacturer (
      id integer identity primary key,
      name varchar (128))"

   "create table product (
      id integer identity primary key,
      name varchar(255),
      description varchar(255),
      category_id integer foreign key references category (id),
      manufacturer_id integer foreign key references manufacturer (id),
      price numeric
    )"

   "insert into category (name) values (('widgets'), ('clothing'), ('toys'), ('food'))"
   "insert into manufacturer (name) values (
       ('Acme Inc'),
       ('Blammo Toy Company'),
       ('Threepwood Pirate Clothing Inc'),
       ('Transgalactic Tools Ltd'),
       ('Omni Consumer Products'))"

   "insert into product (name, description, price, category_id, manufacturer_id) values (
      ('Acme earthquake pills', 'Why wait? Make your own earthquakes! Loads of fun.', 14.99, 3, 0),
      ('Fine leather jacket', 'I''m selling these fine leather jackets', 150, 1, 2),
      ('Log from Blammo!', 'It''s log, log, it''s big, it''s heavy, it''s wood. It''s log, log, it''s better than bad, it''s good.', 24.95, 2, 1),
('Illudium Q-36 explosive space modulator', 'Planets obstructing YOUR view of Venus? Destroy them with the new explosive space modulator!', 49.99, 0, 0),
       ('Blue pants', 'Need a generic pair of blue pants? We got you covered.', 70, 1, 3),
       ('Powerthirst!', 'Feel uncomfortably energetic! Made from real lightning!', 19.95, 3, 4),
       ('Tornado kit', 'Create your own tornado with this easy kit.', 17.50, 0, 0),
       ('Boots of escaping', 'In a jam? Wear these to get out of anything.', 999.95, 1, 2))"
   ])

(defn init-db [c]
  (doseq [sql ddl]
    (next.jdbc/execute! c [sql])))

(def db
  (do
    (Class/forName "org.hsqldb.jdbc.JDBCDriver")
    (doto (DriverManager/getConnection "jdbc:hsqldb:mem:bearsqltest" "SA" "")
      (init-db))))

(use-fixtures :each #(binding [bearsql.core/*db* db] (%)))

(deftest simple-select
  (is (= [#:CATEGORY{:ID 0 :NAME "widgets"}
          #:CATEGORY{:ID 1 :NAME "clothing"}
          #:CATEGORY{:ID 2 :NAME "toys"}
          #:CATEGORY{:ID 3 :NAME "food"}]
         (q select * from category)))

  (is (= [#:CATEGORY{:NAME "widgets"}]
         (q select name from category where id < 1)))

  (let [match (str "%o%")]
    (is (= #{"clothing" "toys" "food"}
           (into #{} (map :CATEGORY/NAME)
                 (q select name from category where name like @match)))))

  (is (= [{:PRODUCT/PRICE 17M,
           :PRODUCT/MANUFACTURER_ID 0,
           :MANUFACTURER/ID 0,
           :MANUFACTURER/NAME "Acme Inc",
           :PRODUCT/ID 6,
           :PRODUCT/NAME "Tornado kit",
           :PRODUCT/CATEGORY_ID 0,
           :CATEGORY/ID 0,
           :PRODUCT/DESCRIPTION "Create your own tornado with this easy kit.",
           :CATEGORY/NAME "widgets"}]
         (q select * from product p
            join manufacturer m on p.manufacturer-id = m.id
            join category c on p.category-id = c.id
            where p.id = 6))))

(deftest single-value
  (is (= "widgets" (q1 select name from category where id = 0))))

(deftest as-alias
  (is (= [{:CATEGORY/CATNAME "clothing"
           :PRODUCT/PRODNAME "Fine leather jacket"}]
         (q select [c.name as catname
                    p.name as prodname]
            from product p join category c on p.category-id = c.id
            where p.id = 1))))

(deftest multiple-from
  (is (= [{:CATEGORY/NAME "widgets" :PRODUCT/NAME "Illudium Q-36 explosive space modulator"}]
         (q select [c.name p.name]
            from [product as p, category as c]
            where p.category-id = c.id
            and p.id = 3))))

(deftest calling-function
  (is (= [{:PRODUCTS "Fine leather jacket, Boots of escaping, Blue pants"}]
         (q select group-concat (distinct p.name order by p.name desc separator ", ") as products
            from product p
            where p.category-id = 1))))

(deftest raw-part
  (is (= ["Acme earthquake pills"
          "Log from Blammo!"
          "Powerthirst!"]
         (mapv :PRODUCT/N
               (q select [:raw "name as n"]
                  from product p
                  where [:raw "p.category_id in (?,?)" 2 3]
                  order by n asc)))))

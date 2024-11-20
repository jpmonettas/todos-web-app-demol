(ns todos.main
  (:require [org.httpkit.server :as http-server]
            [compojure.core :refer [GET POST routes]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [hiccup.core :as h]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;; (def state
;;   (atom {}))

;; (defn add-todo [todo-text]
;;   (let [next-id (inc (count @state))]
;;     (swap! state assoc next-id {:id next-id
;;                                 :text todo-text
;;                                 :done false})))

;; (defn mark-todo-done [todo-id]
;;   (swap! state assoc-in [todo-id :done] true))

;; (defn all-todos []
;;   (->> (vals @state)))

(def database (jdbc/get-datasource {:dbtype "h2" :dbname "todos"}))

(defn init-db []
  (jdbc/execute! database ["
create table todos (
  id int auto_increment primary key,
  text varchar(32),
  done int)"])

  (jdbc/execute! database ["insert into todos(text,done) values('Call doctor',0)"])
  (jdbc/execute! database ["insert into todos(text,done) values('Buy milk',0)"])
  (jdbc/execute! database ["insert into todos(text,done) values('Review pull request',0)"]))

(defn add-todo [todo-text]
  (jdbc/execute!
   database
   ["insert into todos(text,done) values(?,0)" todo-text]))

(defn mark-todo-done [todo-id]
  (jdbc/execute!
   database
   ["update todos set done=1 where id=?" todo-id]))

(defn all-todos []
  (->> (jdbc/execute!
        database
        ["select * from todos"]
        {:builder-fn rs/as-unqualified-lower-maps})
       (mapv (fn [todo] (update todo :done {0 false 1 true})))))

(defn render-main-page []
  {:status 200
   :body
   (h/html
       [:html
        [:head
         [:style "h1 {color:red;}
                  .complete {margin-left: 10px; color: purple;} "]]
        [:body
         [:h1 "TODOs app"]
         [:ul
          (for [{:keys [id text done]} (all-todos)]
            (if done

              ;; :li for done
              [:li [:span text] [:span.complete "Complete"]]

              ;; :li for not done
              [:li
               [:span text]
               [:form {:action "/mark-done" :method "POST"}
                [:input {:name "todo-id" :type "hidden" :value (str id)}]
                [:input {:type "submit" :value "Mark as done"}]]]))]

         [:form {:action "/add-todo" :method "POST"}
          [:label {:for "todo-text"} "New todo:"]
          [:input {:id "todo-text" :name "todo-text" :type "text"}]
          [:input {:type "submit" :value "Add todo"}]]]])})

(def handler
  (routes
   (GET "/" req (render-main-page))

   (POST "/add-todo" req
     (let [todo-text (get-in req [:form-params "todo-text"])]
       (add-todo todo-text)
       (render-main-page)))

   (POST "/mark-done" req
     (let [todo-id (parse-long (get-in req [:form-params "todo-id"]))]
       (mark-todo-done todo-id)
       (render-main-page)))))

(defn -main [& args]
  (println "Populating db...")
  (init-db)
  (println "Starting http server")
  (http-server/run-server
   (-> #'handler
       wrap-params
       (wrap-resource "/publics"))
   {:port 7744})
  (println "All done"))


;;;;;;;;;;;;;;;;;;
;; For the repl ;;
;;;;;;;;;;;;;;;;;;

(comment

  ;; start the http server
  (def server (http-server/run-server
               (-> #'handler
                   wrap-params
                   (wrap-resource "/publics"))
               {:port 7744}))

  ;; to stop the http server
  (server)

  (init-db)

  (all-todos)
  (add-todo "a todo")
  (mark-todo-done 2)

  )

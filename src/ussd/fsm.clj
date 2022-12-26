(ns ussd.fsm
  (:require
   [monger.core :as mg]
   [monger.collection :as mc]
   [monger.operators :as mo]
   [io.pedestal.http.body-params :as body-params]))

(def fsm
  {:start {:init {:text "Welcome to the USSD application.\nWe are registering users.\nPress 1 to continue or just cancel."
                  :state :ready}}
   :ready {:one {:state :query-email
                 :text "Please enter your email address."}}
   :query-email {:empty {:state :query-email
                         :text "Sorry, the text is empty.\nPlease enter an email"}
                 :not-empty {:state :query-username
                             :text "Please enter a username."}}
   :query-username {:empty {:state :query-username
                            :text "Sorry, the text is empty.\nPlease enter a username"}
                    :not-empty {:state :thank-you
                                :text "Thank you for participating in the registration."}}
   :thank-you :finish})

(defn make-via
  [raw-via]
  (cond
    (clojure.string/blank? raw-via) :empty
    (= raw-via "1") :one
    :else :not-empty))

(defn make-stuff
  [fsm current-state via db collection session-id]
  (let [new-state (get-in fsm [current-state via :state])
        _ (println "new-state is -> " new-state)
        new-text (get-in fsm [current-state via :text])
        _ (println "new-text is -> " new-text)]
    (do (mc/update db collection {:session-id session-id} {mo/$set {:current-state new-state}
                                                           mo/$push {:past-state {:from current-state
                                                                                  :via via
                                                                                  :to new-state}}})
        (if (= new-state :thank-you)
          (str "END " new-text)
          (str "CON " new-text)))))

(defn new-stuff
  [fsm db collection session-id phone-number]
  (let [ready-state (get-in fsm [:start :init :state])
        _ (println "ready-state is -> " ready-state)]
    (do (println "db insert -> "(mc/insert db collection {:session-id session-id
                                                          :current-state ready-state
                                                          :phone-number phone-number}))
        (get-in fsm [:start :init :text]))))

(def db (mg/get-db (mg/connect) "ussd-test"))

(defn first-text
  [text]
  (if text
    (-> text
        (clojure.string/split #"\*")
        last
        make-via)
    :nothing))

(def ussd-interceptor
  {:name ::interceptor-extreme
   :enter (fn [context]
            (let [_ (clojure.pprint/pprint context)
                  params (get-in context [:request :form-params])
                  _ (println "form params are: -> ")
                  _ (clojure.pprint/pprint params)
                  session-id (get params :sessionId)
                  phone-number (get params :phoneNumber)
                  network-code (get params :networkCode)
                  service-code (get params :serviceCode)
                  text (get params :text)
                  _ (println "text is -> " text)
                  via (first-text text)
                  _ (println "via is -> " via)
                  collection "ussd-sessions"
                  ussd-session (mc/find-one-as-map db collection {:session-id session-id})
                  _ (println "ussd session is -> " ussd-session)
                  current-state (keyword (:current-state ussd-session))
                  db db]
              (if ussd-session
                (assoc context ::my-response (make-stuff fsm current-state via db collection session-id))
                (assoc context ::my-response (new-stuff fsm db collection session-id phone-number)))))
   :leave (fn [context]
            (let [my-response (get context ::my-response)]
              (assoc context :response {:status 200
                                        :body my-response})))})

(def interceptors
  [(body-params/body-params) ussd-interceptor])

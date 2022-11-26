(ns ussd.fsm-utils
  (:require
   [clojure.string :as str]
   [clojure.edn :as edn]
   [ussd.sms :as sms]))

(defn check-via
  [via]
  (let [choices (:choices (edn/read-string (slurp "/home/kevinmungai/clojure/ussd/ussd_fsm.edn")))]
    (if (contains? choices via)
      via
      (if (str/blank? via)
        :empty
        :not-empty))))

(defn user-response-for-query
  [past-states query]
  (some-> (some #(when (= (:from %) query) %) past-states)
          :user-response))

(defn print-to-file
  [file data]
  (with-open [writer (clojure.java.io/writer file)]
    (clojure.pprint/pprint data writer)))

(defn get-next-state
  [fsm current-state via]
  (let [checked-via (check-via via)]
    (get-in fsm [current-state checked-via :next-state])))

(defn get-next-text
  [fsm current-state via]
  (let [checked-via (check-via via)]
    (get-in fsm [current-state checked-via :text])))

(defn does-session-exist?
  [storage-location session-id]
  (let [parsed-text (slurp storage-location)]
    (or (str/blank? parsed-text) (contains? (edn/read-string parsed-text) session-id))))

(defn new-storage
  [{:keys [storage-location fsm session-id phone-number network-code service-code text]}]
  (let [next-state (get-in fsm [:start :init :next-state])
        next-text (get-in fsm [:start :init :text])
        storage (edn/read-string (slurp storage-location))]
    (if storage
      (assoc storage session-id {:phone-number phone-number
                                 :network-code network-code
                                 :service-code service-code
                                 :text text
                                 :current-state next-state
                                 :current-text-response next-text
                                 :past-state [{:from :start
                                               :via :init
                                               :to next-state}]})
      {session-id {:phone-number phone-number
                   :network-code network-code
                   :service-code service-code
                   :text text
                   :current-state next-state
                   :current-text-response next-text
                   :past-state [{:from :start
                                 :via :init
                                 :to next-state}]}})))

(defn existing-storage
  [{:keys [storage-location fsm session-id phone-number network-code service-code text]}]
  (let [storage (edn/read-string (slurp storage-location))
        current-state (get-in storage [session-id :current-state])
        choice-or-not (last (str/split text #"\*"))
        next-state (get-next-state fsm current-state choice-or-not)
        next-text (get-next-text fsm current-state choice-or-not)
        session-map (get storage session-id)]
    (if next-state
      (->> (merge session-map {:text text
                               :current-state next-state
                               :current-text-response next-text
                               :past-state (conj (:past-state session-map) {:from current-state
                                                                            :via (check-via choice-or-not)
                                                                            :to next-state
                                                                            :user-response choice-or-not})})
           (assoc storage session-id))
      (->> (merge session-map {:text text
                               :past-state (conj (:past-state session-map) {:from current-state
                                                                            :via choice-or-not
                                                                            :user-error true
                                                                            :user-response choice-or-not
                                                                            :to current-state})})
           (assoc storage session-id)))))

(defn new-session-scenario
  [{:keys [storage-location fsm session-id phone-number network-code service-code text] :as all}]
  (let [storage (new-storage all)
        current-text-response (get-in storage [session-id :current-text-response])
        current-state (get-in storage [session-id :current-state])]
    (do (print-to-file storage-location storage)
        (if (= current-state :finish)
          (str "END " current-text-response)
          (str "CON " current-text-response)))))

(defn existing-session-scenario
  [{:keys [storage-location fsm session-id phone-number network-code service-code text] :as all}]
  (let [storage (existing-storage all)
        current-text-response (get-in storage [session-id :current-text-response])
        current-state (get-in storage [session-id :current-state])
        email (user-response-for-query (get-in storage [session-id :past-state]) :query-email)
        username (user-response-for-query (get-in storage [session-id :past-state]) :query-username)]
    (do (print-to-file storage-location storage)
        (if (= current-state :finish)
          (do
            (sms/send-sms
             {:phone-number phone-number
              :message (str "Thank you for participating. You have successfully registered\nemail: " email "\nusername: " username "\n\n")})
            (str "END " current-text-response))
          (str "CON " current-text-response)))))

(defn ussd
  [{:keys [storage-location fsm session-id phone-number network-code service-code text]
    :as all}]
  (if (does-session-exist? storage-location session-id)
    (existing-session-scenario all)
    (new-session-scenario all)))

(def ussd-interceptor
  {:name ::ussd-interceptor-2
   :enter (fn [context]
            (let [params (get-in context [:request :form-params])
                  session-id (get params :sessionId)
                  phone-number (get params :phoneNumber)
                  network-code (get params :networkCode)
                  service-code (get params :serviceCode)
                  text (get params :text)
                  fsm (:fsm (edn/read-string (slurp "/home/kevinmungai/clojure/ussd/ussd_fsm.edn")))]
              (assoc context :response {:status 200
                                        :body (ussd {:storage-location "/home/kevinmungai/clojure/ussd/storage/database.edn"
                                                     :fsm fsm
                                                     :session-id session-id
                                                     :phone-number phone-number
                                                     :network-code network-code
                                                     :service-code service-code
                                                     :text text})})))})

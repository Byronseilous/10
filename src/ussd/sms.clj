(ns ussd.sms
  (:require
   [clj-http.client :as clj-http :refer [post]]
   [cheshire.core :as cheshire]))

(def api-key "ec89b08ea15be11f084a595560c71f8dfc692ee25da9a24868f9fbd85e0dbbd8")

(defn send-sms
  [{:keys [api-key username phone-number message]
    :or {api-key "ec89b08ea15be11f084a595560c71f8dfc692ee25da9a24868f9fbd85e0dbbd8"
         username "sandbox"
         phone-number "+254715761632"}}]
  (post "https://api.sandbox.africastalking.com/version1/messaging"
        {:headers {"apiKey" api-key}
         :accept :json
         :debug true
         :form-params {:username username
                       :to phone-number
                       :message message}}))

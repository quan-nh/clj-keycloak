(ns clj-keycloak.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clj-http.client :as http]
            [hiccup.core :refer [html]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect]]))

(def keycloak-url "http://localhost:8080/auth")
(def openid-connect (str keycloak-url "/realms/master/protocol/openid-connect"))
(def admin-endpoint (str keycloak-url "/admin"))

(defn- user-info [token]
  (:body (http/get (str openid-connect "/userinfo")
                   {:oauth-token token
                    :as          :json})))

(defn- groups
  "Get Groups
  https://www.keycloak.org/docs-api/6.0/rest-api/#_groups_resource"
  [token]
  (:body (http/get (str admin-endpoint "/realms/test/groups")
                   {:oauth-token token
                    :as          :json})))

(defn- users
  "Get Users
  https://www.keycloak.org/docs-api/6.0/rest-api/#_users_resource"
  [token]
  (:body (http/get (str admin-endpoint "/realms/test/users")
                   {:oauth-token token
                    :as          :json})))

(defroutes app-routes
           (GET "/" req
             (if-let [{:keys [token expires refresh-token]} (-> req :oauth2/access-tokens :keycloak)]
               (html [:div
                      [:a {:href "/logout"} "logout"]
                      [:div "hello " (:preferred_username (user-info token))]
                      [:div "groups"
                       [:ul
                        (for [group (groups token)]
                          [:li (:name group)])]]
                      [:div "users"
                       [:ul
                        (for [user (users token)]
                          [:li (:username user)])]]])
               (html [:a {:href "/oauth2/keycloak"} "login"])))

           (GET "/logout" []
             (-> (redirect (str openid-connect "/logout?redirect_uri=http://localhost:3000/"))
                 (assoc-in [:session :ring.middleware.oauth2/access-tokens] nil)))

           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-oauth2
        {:keycloak
         {:authorize-uri    (str openid-connect "/auth")
          :access-token-uri (str openid-connect "/token")
          :client-id        "clj-keycloak"
          :client-secret    "secret"
          ;:scopes           ["email"]
          :launch-uri       "/oauth2/keycloak"
          :redirect-uri     "/oauth2/keycloak/callback"
          :landing-uri      "/"}})
      ;wrap-params
      (wrap-defaults site-defaults)
      #_(wrap-defaults (-> site-defaults (assoc-in [:session :cookie-attrs :same-site] :lax)))))

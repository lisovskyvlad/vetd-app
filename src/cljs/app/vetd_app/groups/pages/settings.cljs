(ns vetd-app.groups.pages.settings
  (:require [vetd-app.ui :as ui]
            [vetd-app.common.components :as cc]
            [vetd-app.buyers.components :as bc]
            [vetd-app.common.fx :as cfx]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [clojure.string :as s]))

(rf/reg-event-fx
 :g/nav-settings
 (constantly
  {:nav {:path "/c/settings"}
   :analytics/track {:event "Navigate"
                     :props {:category "Navigation"
                             :label "Groups Settings"}}}))

(rf/reg-event-fx
 :g/route-settings
 (fn [{:keys [db]}]
   {:db (assoc db
               :page :g/settings
               :page-params {:fields-editing #{}})
    :analytics/page {:name "Groups Settings"}}))

(rf/reg-event-fx
 :g/add-orgs-to-group
 (fn [{:keys [db]} [_ group-id org-ids]]
   {:ws-send {:payload {:cmd :g/add-orgs-to-group
                        :return {:handler :g/add-orgs-to-group-return
                                 :group-id group-id
                                 :org-ids org-ids}
                        :group-id group-id
                        :org-ids org-ids}}
    :analytics/track {:event "Add Organization"
                      :props {:category "Community"}}}))

(rf/reg-event-fx
 :g/add-orgs-to-group-return
 (fn [{:keys [db]} [_ _ {{:keys [group-id org-ids]} :return}]]
   {:toast {:type "success"
            :title (str "Organization" (when (> (count org-ids) 1) "s") " added to your community!")}
    :dispatch [:stop-edit-field (str "add-orgs-to-group-" group-id)]}))

;; remove an org from a group
(rf/reg-event-fx
 :g/remove-org
 (fn [{:keys [db]} [_ group-id org-id]]
   {:ws-send {:payload {:cmd :g/remove-org
                        :return {:handler :g/remove-org-return}
                        :group-id group-id
                        :org-id org-id}}
    :analytics/track {:event "Remove Organization"
                      :props {:category "Community"}}}))

(rf/reg-event-fx
 :g/remove-org-return
 (fn [{:keys [db]}]
   {:toast {:type "success"
            :title "Organization removed from your community."}}))

(rf/reg-event-fx
 :g/add-discount-to-group.submit
 (fn [{:keys [db]} [_ group-id product-id details]]
   (cfx/validated-dispatch-fx db
                              [:g/add-discount-to-group group-id product-id details]
                              #(cond
                                 (s/blank? product-id) [(keyword (str "add-discount-to-group" group-id ".product-id"))
                                                        "You must select a product."]
                                 (s/blank? details) [(keyword (str "add-discount-to-group" group-id ".details"))
                                                     "Discount details cannot be blank."]
                                 :else nil))))

(rf/reg-event-fx
 :g/add-discount-to-group
 (fn [{:keys [db]} [_ group-id product-id details]]
   {:ws-send {:payload {:cmd :g/set-discount
                        :return {:handler :g/add-discount-to-group-return
                                 :group-id group-id}
                        :group-id group-id
                        :product-id product-id
                        :descr details}}
    :analytics/track {:event "Add Discount"
                      :props {:category "Community"
                              :label product-id}}}))

(rf/reg-event-fx
 :g/add-discount-to-group-return
 (fn [{:keys [db]} [_ _ {{:keys [group-id]} :return}]]
   {:toast {:type "success"
            :title "Discount added to community!"}
    :dispatch [:stop-edit-field (str "add-discount-to-group-" group-id)]}))

(rf/reg-event-fx
 :g/delete-discount
 (fn [{:keys [db]} [_ discount-id]]
   {:ws-send {:payload {:cmd :g/delete-discount
                        :return {:handler :g/delete-discount-return}
                        :discount-id discount-id}}
    :analytics/track {:event "Delete Discount"
                      :props {:category "Community"
                              :label discount-id}}}))

(rf/reg-event-fx
 :g/delete-discount-return
 (fn [{:keys [db]}]
   {:toast {:type "success"
            :title "Discount deleted from community."}}))

;;;; Components
(defn c-add-orgs-form [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])
        bad-input& (rf/subscribe [:bad-input])
        value& (r/atom [])
        options& (r/atom []) ; options from search results + current values
        search-query& (r/atom "")
        orgs->options (fn [orgs]
                        (for [{:keys [id oname]} orgs]
                          {:key id
                           :text oname
                           :value id}))]
    (fn [group]
      (let [orgs& (rf/subscribe
                   [:gql/q
                    {:queries
                     [[:orgs {:_where {:oname {:_ilike (str "%" @search-query& "%")}}
                              :_limit 25
                              :_order_by {:oname :asc}}
                       [:id :oname]]]}])
            org-ids-already-in-group (set (map :id (:orgs group)))
            _ (when-not (= :loading @orgs&)
                (let [options (->> @orgs&
                                   :orgs
                                   orgs->options ; now we have options from gql sub
                                   ;; (this dumbly actually keeps everything, but that seems fine)
                                   (concat @options&) ; keep options for the current values
                                   distinct
                                   (remove (comp (partial contains? org-ids-already-in-group) :value)))]
                  (when-not (= @options& options)
                    (reset! options& options))))]
        [:> ui/Form {:as "div"
                     :class "popup-dropdown-form"} ;; popup is a misnomer here
         [:> ui/FormField {:style {:padding-top 7
                                   :width "100%"}
                           ;; this class combo w/ width 100% is a hack
                           :class "ui action input"}
          [:> ui/Dropdown {:loading (= :loading @orgs&)
                           :options @options&
                           :placeholder "Search organizations..."
                           :search true
                           :selection true
                           :multiple true
                           ;; :auto-focus true ;; TODO this doesn't work
                           :selectOnBlur false
                           :selectOnNavigation true
                           :closeOnChange true
                           :allowAdditions false ;; TODO this should be changed to true when we allow invites of new orgs
                           ;; :additionLabel "Hit 'Enter' to Add "
                           ;; :onAddItem (fn [_ this]
                           ;;              (->> this
                           ;;                   .-value
                           ;;                   vector
                           ;;                   ui/as-dropdown-options
                           ;;                   (swap! options& concat)))
                           :onSearchChange (fn [_ this] (reset! search-query& (aget this "searchQuery")))
                           :onChange (fn [_ this] (reset! value& (.-value this)))}]
          [:> ui/Button
           {:color "teal"
            :disabled (empty? @value&)
            :on-click #(rf/dispatch [:g/add-orgs-to-group (:id group) (js->clj @value&)])}
           "Add"]]]))))

(defn c-org
  [org group]
  (let [popup-open? (r/atom false)]
    (fn [{:keys [id idstr oname memberships] :as org}
         {:keys [gname] :as group}]
      (let [num-members (count memberships)]
        [cc/c-field {:label [:<>
                             [:> ui/Popup
                              {:position "bottom right"
                               :on "click"
                               :open @popup-open?
                               :on-close #(reset! popup-open? false)
                               :content (r/as-element
                                         [:div
                                          [:h5 "Are you sure you want to remove " oname " from " gname "?"]
                                          [:> ui/ButtonGroup {:fluid true}
                                           [:> ui/Button {:on-click #(reset! popup-open? false)}
                                            "Cancel"]
                                           [:> ui/Button {:on-click (fn []
                                                                      (reset! popup-open? false)
                                                                      (rf/dispatch [:g/remove-org (:id group) id]))
                                                          :color "red"}
                                            "Remove"]]])
                               :trigger (r/as-element
                                         [:> ui/Label {:on-click #(swap! popup-open? not)
                                                       :as "a"
                                                       :style {:float "right"
                                                               :margin-top 5}}
                                          [:> ui/Icon {:name "remove"}]
                                          "Remove"])}]
                             oname]
                     :value [:<> (str num-members " member" (when-not (= num-members 1) "s") " ")
                             [:> ui/Popup
                              {:position "bottom left"
                               :wide "very"
                               :content (let [max-members-show 15]
                                          (str (s/join ", " (->> memberships
                                                                 (map (comp :uname :user))
                                                                 (take max-members-show)))
                                               (when (> num-members max-members-show)
                                                 (str " and " (- num-members max-members-show) " more."))))
                               :trigger (r/as-element
                                         [:> ui/Icon {:name "question circle"}])}]]}]))))

(defn c-add-discount-form [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])
        bad-input& (rf/subscribe [:bad-input])
        product& (r/atom nil)
        details& (r/atom "")
        options& (r/atom []) ; options from search results + current values
        search-query& (r/atom "")
        products->options (fn [products]
                            (for [{:keys [id pname]} products]
                              {:key id
                               :text pname
                               :value id}))]
    (fn [group]
      (let [products& (rf/subscribe
                       [:gql/q
                        {:queries
                         [[:products {:_where {:pname {:_ilike (str "%" @search-query& "%")}}
                                      :_limit 100
                                      :_order_by {:pname :asc}}
                           [:id :pname]]]}])
            _ (when-not (= :loading @products&)
                (let [options (->> @products&
                                   :products
                                   products->options ; now we have options from gql sub
                                   ;; (this dumbly actually keeps everything, but that seems fine)
                                   (concat @options&) ; keep options for the current values
                                   distinct)]
                  (when-not (= @options& options)
                    (reset! options& options))))]
        [:> ui/Form
         [:> ui/FormField {:error (= @bad-input& (keyword (str "add-discount-to-group" (:id group) ".product-id")))
                           :style {:padding-top 7}}
          [:> ui/Dropdown {:loading (= :loading @products&)
                           :options @options&
                           :placeholder "Search products..."
                           :search true
                           :selection true
                           :multiple false
                           ;; :auto-focus true ;; TODO this doesn't work
                           :selectOnBlur false
                           :selectOnNavigation true
                           :closeOnChange true
                           :onSearchChange (fn [_ this] (reset! search-query& (aget this "searchQuery")))
                           :onChange (fn [_ this] (reset! product& (.-value this)))}]]
         [:> ui/FormField {:error (= @bad-input& (keyword (str "add-discount-to-group" (:id group) ".details")))}
          [:> ui/Input
           {:placeholder "Discount details..."
            :fluid true
            :on-change #(reset! details& (-> % .-target .-value))
            :action (r/as-element
                     [:> ui/Button {:on-click #(rf/dispatch [:g/add-discount-to-group.submit
                                                             (:id group)
                                                             (js->clj @product&)
                                                             @details&])
                                    :disabled (nil? @product&)
                                    :color "blue"}
                      "Add"])}]]]))))

(defn c-discount
  [discount]
  (let [popup-open? (r/atom false)]
    (fn [{:keys [id idstr pname
                 group-discount-descr ref-id ; the discount ID
                 vendor] :as discount}]
      [cc/c-field {:label [:<>
                           [:> ui/Popup
                            {:position "bottom right"
                             :on "click"
                             :open @popup-open?
                             :on-close #(reset! popup-open? false)
                             :content (r/as-element
                                       [:div
                                        [:h5 "Are you sure you want to delete this discount?"]
                                        [:> ui/ButtonGroup {:fluid true}
                                         [:> ui/Button {:on-click #(reset! popup-open? false)}
                                          "Cancel"]
                                         [:> ui/Button {:on-click (fn []
                                                                    (reset! popup-open? false)
                                                                    (rf/dispatch [:g/delete-discount ref-id]))
                                                        :color "red"}
                                          "Delete"]]])
                             :trigger (r/as-element
                                       [:> ui/Label {:on-click #(swap! popup-open? not)
                                                     :as "a"
                                                     :style {:float "right"
                                                             :margin-top 5}}
                                        [:> ui/Icon {:name "remove"}]
                                        "Delete"])}]
                           [:a.name {:on-click #(rf/dispatch [:b/nav-product-detail idstr])}
                            pname]
                           [:small " by " (:oname vendor)]]
                   :value group-discount-descr}])))

(defn c-orgs
  [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])]
    (fn [{:keys [id orgs] :as group}]
      (let [field-name (str "add-orgs-to-group-" id)]
        [bc/c-profile-segment
         {:title [:<>
                  (if (@fields-editing& field-name)
                    [:> ui/Label {:on-click #(rf/dispatch [:stop-edit-field field-name])
                                  :as "a"
                                  :style {:float "right"}}
                     "Cancel"]
                    [:> ui/Label {:on-click #(rf/dispatch [:edit-field field-name])
                                  :as "a"
                                  :color "teal"
                                  :style {:float "right"}}
                     [:> ui/Icon {:name "add group"}]
                     "Add Organization"])
                  "Organizations"
                  (when (@fields-editing& field-name)
                    [c-add-orgs-form group])]}
         (for [org orgs]
           ^{:key (:id org)}
           [c-org org group])]))))

(defn c-discounts
  [group]
  (let [fields-editing& (rf/subscribe [:fields-editing])]
    (fn [{:keys [id discounts] :as group}]
      (let [field-name (str "add-discount-to-group-" id)]
        [bc/c-profile-segment
         {:title [:<>
                  (if (@fields-editing& field-name)
                    [:> ui/Label {:on-click #(rf/dispatch [:stop-edit-field field-name])
                                  :as "a"
                                  :style {:float "right"}}
                     "Cancel"]
                    [:> ui/Label {:on-click #(rf/dispatch [:edit-field field-name])
                                  :as "a"
                                  :color "blue"
                                  :style {:float "right"}}
                     [:> ui/Icon {:name "dollar"}]
                     "Add Discount"])
                  "Discounts"
                  (when (@fields-editing& field-name)
                    [c-add-discount-form group])]}
         (for [discount discounts]
           ^{:key (:id discount)}
           [c-discount discount])]))))

(defn c-group
  [{:keys [gname] :as group}]
  [:> ui/Grid {:stackable true
               :style {:padding-bottom 35}} ; in case they are admin of multiple communities
   [:> ui/GridRow
    [:> ui/GridColumn {:computer 16 :mobile 16}
     [:h1 {:style {:text-align "center"}}
      gname]]]
   [:> ui/GridRow
    [:> ui/GridColumn {:computer 8 :mobile 16}
     [c-orgs group]]
    [:> ui/GridColumn {:computer 8 :mobile 16}
     [c-discounts group]]]])

(defn c-groups
  [groups]
  [:div
   (for [group groups]
     ^{:key (:id group)}
     [c-group group])])

(defn c-page []
  (let [org-id& (rf/subscribe [:org-id])
        groups& (rf/subscribe [:gql/sub
                               {:queries
                                [[:groups {:admin-org-id @org-id&
                                           :deleted nil}
                                  [:id :gname
                                   [:orgs
                                    [:id :oname
                                     [:memberships
                                      [:id
                                       [:user
                                        [:id :uname]]]]]]
                                   [:discounts {:ref-deleted nil}
                                    ;; NOTE :id is product id and idstr
                                    ;; ref-id is the id of the discount
                                    [:ref-id :id :idstr :pname
                                     :group-discount-descr
                                     [:vendor
                                      [:id :oname]]]]]]]}])]
    (fn []
      (if (= :loading @groups&)
        [cc/c-loader]
        [c-groups (:groups @groups&)]))))

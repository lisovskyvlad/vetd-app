(ns vetd-app.util
  (:require [clojure.string]
            [clojure.set]
            [re-frame.interop :refer [deref? reagent-id]]
            [reagent.ratom :as rr]
            [re-frame.registrar :as rf-reg]))

(defn now [] (.getTime (js/Date.)))

(defn kw->str
  [kw]
  (str (when-let [n (and (keyword? kw)
                         (namespace kw))]
         (str n "/"))
       (name kw)))

(defn ->vec [v]
  (cond (vector? v) v
        (sequential? v) (vec v)
        (map? v) [v]
        (coll? v) (vec v)
        :else [v]))

(defn fmap [f m]
  (->> (for [[k v] m]
         [k (f v)])
       (into {})))

(defn keep-kv [f m]
  (->> (for [[k v] m]
         (f k v))
       (remove nil?)
       (into {})))

(defn inline-colls
  [tag & tail]
  (into [tag]
        (if (sequential? tail)
          (->> tail
               (map #(if (and (sequential? %)
                              (-> %
                                  first
                                  sequential?))
                       % [%]))
               (apply concat)
               vec)
          tail)))

;; TODO this is not necessary??!!?!?!?!?
(def ic inline-colls)

(defn flexer-xfrm-attrs
  [attrs]
  (update attrs :style
          clojure.set/rename-keys
          {:f/dir :flex-direction
           :f/wrap :flex-wrap
           :f/flow :flex-flow
           :f/grow :flex-grow
           :f/shrink :flex-shrink
           :f/basis :flex-basis}))

(defn flexer-merge-attrs
  [a1 a2]
  (merge a1 a2
         {:class (-> (into (:class a1)
                           (:class a2))
                     distinct
                     vec)
          :style (merge {}
                        (:style a1)
                        (:style a2))}))

(defn flexer
  [{:keys [p c]} & children]
  (let [children' (apply concat children)]
    [ic :div (flexer-xfrm-attrs
              (assoc-in p [:style :display] :flex))
     (for [ch children']
       (let [[attrs & body] ch]
         (into [:div (flexer-xfrm-attrs
                      (flexer-merge-attrs c attrs))]
               body)))]))

(defn find-map-heads [v]
  (cond (-> v sequential? not) v
        (-> v first map?) [v]
        (-> v first sequential?) (->> v
                                      (mapcat find-map-heads)
                                      vec)
        :else (throw
               (js/Error. (str "find-map-heads -- what is this? " v)))))

(defn flx
  [{:keys [p c]} & children]
  (def c1 children)
  (let [chs (find-map-heads children)]
    ;; `into` avoids unique key warnings
    (into [:div (flexer-xfrm-attrs
                 (assoc-in p [:style :display] :flex))]
          (for [ch chs]
            (let [[attrs & body] ch]
              (into [:div (flexer-xfrm-attrs
                           (flexer-merge-attrs c attrs))]
                    body))))))

(defn- map-signals
  "Runs f over signals. Signals may take several
  forms, this function handles all of them."
  [f signals]
  (cond
    (sequential? signals) (map f signals)
    (map? signals) (fmap f signals)
    (deref? signals) (f signals)
    :else '()))

(defn- deref-input-signals
  [signals query-id]
  (let [dereffed-signals (map-signals deref signals)]
    (cond
      (sequential? signals) (map deref signals)
      (map? signals) (fmap deref signals)
      (deref? signals) (deref signals)
      :else (println :error "datarumpus: in the reg-sub for" query-id ", the input-signals function returns:" signals))
    dereffed-signals))



(defn reg-sub-special
  [query-id inputs-fn init-fn]
  (let [err-header     (str "reg-sub-special for " query-id ", ")]
    (rf-reg/register-handler
     :sub
     query-id
     (fn subs-handler-fn
       ([db query-vec]
        (let [subscriptions (inputs-fn query-vec)
              reaction-id   (atom nil)
              {:keys [computation auto-set on-dispose on-set]} (init-fn query-vec)
              reaction      (rr/make-reaction
                             (fn []
                               (computation (deref-input-signals subscriptions query-id)
                                            query-vec))
                             :auto-set auto-set
                             :on-dispose on-dispose
                             :on-set on-set)]

          (reset! reaction-id (reagent-id reaction))
          reaction))
       #_       ([db query-vec dyn-vec]
                 (let [subscriptions (inputs-fn query-vec dyn-vec)
                       reaction-id   (atom nil)
                       {:keys [computation auto-set on-dispose on-set]} (init-fn query-vec)              
                       reaction      (apply make-reaction
                                            (fn []
                                              (computation (deref-input-signals subscriptions query-id)
                                                           query-vec
                                                           dyn-vec))
                                            :auto-set auto-set
                                            :on-dispose on-dispose
                                            :on-set on-set)]

                   (reset! reaction-id (reagent-id reaction))
                   reaction))))))
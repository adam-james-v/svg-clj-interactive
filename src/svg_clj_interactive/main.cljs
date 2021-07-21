(ns ^:figwheel-hooks svg-clj-interactive.main
  (:require [clojure.string :as str]
            [reagent.dom :as rdom]
            [reagent.core :as r]
            [svg-clj.composites :as comp :refer [svg]]
            [svg-clj.utils :as utils]
            [svg-clj.elements :as el]
            [svg-clj.path :as path]
            [svg-clj.transforms :as tf]
            [svg-clj.parametric :as p]
            [svg-clj.layout :as lo]
            [sci.core :as sci]
            [cljs.pprint]))

(def default-editor-content-01
  "(ns svg-clj-interactive.main
  (:require [clojure.string :as str]
            [svg-clj.composites :as comp :refer [svg]]
            [svg-clj.utils :as utils]
            [svg-clj.elements :as el]
            [svg-clj.path :as path]
            [svg-clj.transforms :as tf]
            [svg-clj.parametric :as p]
            [svg-clj.layout :as lo]))

(-> (el/circle 50)
    (tf/style {:fill \"skyblue\"
               :stroke \"slategray\"
               :stroke-width \"3px\"
               :opacity 0.7}))")

(def default-editor-content-02
  "(ns svg-clj-interactive.main
  (:require [clojure.string :as str]
            [svg-clj.composites :as comp :refer [svg]]
            [svg-clj.utils :as utils]
            [svg-clj.elements :as el]
            [svg-clj.path :as path]
            [svg-clj.transforms :as tf]
            [svg-clj.parametric :as p]
            [svg-clj.layout :as lo]))

(defn flip-y
  [pts]
  (mapv #(utils/v* % [1 -1]) pts))

(defn petal
  [cpts]
  (let [beza (apply path/bezier cpts)
        bezb (apply path/bezier (flip-y cpts))
        shape (tf/merge-paths beza bezb)
        ctr (tf/centroid shape)]
    (-> shape
        (tf/rotate -90.0001)
        (tf/translate (utils/v* ctr [-1 -1])))))

(defn petal-ring
  [petal r n]
  (el/g
   (lo/distribute-on-curve
    (repeat n petal)
    (p/circle r))))

;; You can 'push' keys to the state.
;; they generate a slider with value 0 to 100, initiated at 1
;; and return that value (which reacts to slider changes)
(def a (int (/ (<< :a) 10)))
(def b (<< :b))

;; The parameters will update whenever you push a key.
;; Try uncommenting the next line.
;; (def c (/ (<< :c) 100))

(def petal-01
  (-> (petal [[0 0] [-60 -50] [50 -20] [75 0]])
      (tf/style {:fill \"#ff8b94\"
                 :stroke \"#ffaaa5\"
                 :stroke-width \"4px\"
                 :stroke-linecap \"round\"})))

(def petal-ring-01 (petal-ring petal-01 (* b 2) (+ 3 a)))

petal-ring-01")

(def state
  (r/atom {:dwg {:input-type :drawing
                 :value default-editor-content-02}
           :zoom {:input-type :slider :value 100 :min 10 :max 500}}))

(defn >>
  [k]
  (let [{:keys [input-type]} (get @state k)
        v (if (= input-type :drawing) :result :value)]
  (get-in @state [k v])))

(defn <<
  [k]
  (when (and (not= (str k) ":")
             (keyword? k)
             (not (contains? @state k)))
    (let [ctrl {:input-type :slider :value 1 :min 0 :max 100}]
      (swap! state assoc k ctrl)))
  (>> k))

(def my-ns-map
  {'svg-clj.composites (ns-publics 'svg-clj.composites)
   'svg-clj.utils (ns-publics 'svg-clj.utils)
   'svg-clj.elements (ns-publics 'svg-clj.elements)
   'svg-clj.path (ns-publics 'svg-clj.path)
   'svg-clj.transforms (ns-publics 'svg-clj.transforms)
   'svg-clj.parametric (ns-publics 'svg-clj.parametric)
   'svg-clj.layout (ns-publics 'svg-clj.layout)
   'svg-clj-interactive.main {'state state
                              '>> >>
                              '<< <<}})

(def sci-ns-str
  "(ns svg-clj-interactive.main
  (:require [clojure.string :as str]
            [svg-clj.composites :as comp :refer [svg]]
            [svg-clj.utils :as utils]
            [svg-clj.elements :as el]
            [svg-clj.path :as path]
            [svg-clj.transforms :as tf]
            [svg-clj.parametric :as p]
            [svg-clj.layout :as lo]))")

(def sci-ctx (sci/init {:namespaces my-ns-map}))

(defn sci-eval
  [str]
  (let [f (fn [str]
            (->> (str/join "\n" [sci-ns-str str])
                 (sci/eval-string* sci-ctx)))]
    (try
      (f str)
      (catch :default e (. e -message)))))

(defn eval-state-param
  [[param {:keys [input-type value] :as ctrl}]]
  (when (= input-type :drawing)
    [param (merge ctrl {:result (sci-eval value)})]))

(defn eval-state
  []
  (let [new-state (mapv eval-state-param @state)]
    (reset! state (merge @state (into {} new-state)))))

;; populate any textarea state with the eval result
(eval-state)

(defn editor-did-mount
  [[param {:keys [value _] :as ctrl}]]
  (fn [this]
    (let [cm (.fromTextArea  js/CodeMirror
                             (rdom/dom-node this)
                             #js {:mode "clojure"
                                  :theme "nord"
                                  :lineNumbers true
                                  :smartIndent true
                                  :tabSize 2})]
      (.setSize cm 450 450)
      (.on cm "change"
           (fn [e]
             (let [new-value (.getValue e)
                   new-result (sci-eval new-value)
                   new-ctrl (-> ctrl
                                (assoc :value new-value)
                                (assoc :result new-result))]
               (swap! state
                      (fn [data]
                        (-> data
                            (assoc param new-ctrl))))))))))

(defn editor
  [[_ {:keys [value _]} :as state-entry]]
  (r/create-class
   {:render
    (fn [e] [:textarea
              {:default-value value}])
    :component-did-mount (editor-did-mount state-entry)}))

(defmulti control
  (fn [[_ {:keys [input-type]}]]
    input-type))

(defmethod control :slider
  [[param {:keys [value min max step] :as ctrl}]]
  [:div {:width "70%" :key param}
   [:span {:style {:font-weight "bold"
                   :display "inline-block"
                   :width "40px"
                   :text-align "right"}} (name param)]
   [:input {:type "range" :value value :min min :max max :step step
            :style {:width "200px"
                    :padding 0
                    :vertical-align "middle"
                    :margin "0px 10px"}
            :on-change
            (fn [e]
              (let [new-value (js/parseInt (.. e -target -value))
                    new-ctrl (assoc ctrl :value new-value)]
                (swap! state
                       (fn [data]
                         (-> data
                             (assoc param new-ctrl))))
                (eval-state)))}]
   [:span value]])

(defn renderable?
  [elem]
  (when (seqable? elem)
    #_(and (seqable? elem)
           (not= sci.impl.vars/SciVar (type elem)))
    (#{:svg :text :g :rect :circle :ellipse :line :polygon :polyline :path :image} (first elem))))

(defn wrap-svg
  [elem]
  [:div {:style {:width "450px"
                 :height "450px"
                 :margin "7px"
                 :border-style "solid"
                 :border-width "1px"
                 :border-radius "11px 11px 4px 11px"
                 :border-color "slategray"
                 :overflow "auto"
                 :resize "both"}}
   (if (renderable? elem)
     (let [sc (/ (>> :zoom) 100)
           edge-offset 10
           [w h] (-> (tf/bb-dims elem)
                     (utils/v* [sc sc])
                     (utils/v+ [edge-offset edge-offset]))
           [w2 h2] (utils/v* [2 2] [w h])
           elem (if (= :svg (first elem))
                  (drop 2 elem)
                  elem)]
       [:svg {:width (+ w edge-offset)
              :height (+ h edge-offset)
              :viewBox (str/join " " [(- (/ w 2)) (- (/ h 2)) w h])
              :xmlns "http://www.w3.org/2000/svg"}
        (-> (el/g elem)
            (tf/scale [sc sc]))])
     [:pre [:code "Waiting for renderable content"]])])

(defmethod control :drawing
  [[param {:keys [value result] :as ctrl} :as state-entry]]
  (let [zoom [:zoom (:zoom @state)]
        zoom-ctrl [control zoom]]
    [:<>
    [:div {:style {:width "100%"
                   :display "flex"
                   :flex-flow "wrap"
                   :justify-content "center"}}
     [editor state-entry]
     [:div (wrap-svg (get-in @state [param :result]))
      [:span {:style {:position "relative"
                      :left "35px"}} zoom-ctrl]]]
     [:pre {:style {:max-width "700px"
                    :max-height "300px"
                    :margin "0 auto"}}
      [:code.block (with-out-str (cljs.pprint/pprint result))]]]))

(defn doc []
  [:<>
   [:h1 {:style {:width "100%"
                 :text-align "center"}} "svg-clj"]
   (into [:div {:style
                {:width "400px"
                 :margin "0 auto"
                 :display "flex"
                 :flex-direction "column"
                 :justify-content "center"}}
          [:h3 "Parameters"]]
         (for [param (dissoc @state :zoom :dwg)]
                 [control param]))
   [control [:dwg (:dwg @state)]]])

(defn mount [app]
  (rdom/render [app] (js/document.getElementById "root")))

(mount doc)
(defn ^:after-load re-render [] (mount doc))
(defonce go (do (mount doc) true))

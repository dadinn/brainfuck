(ns dadinn.brainfuck
  (:require
   [clojure.string :as s]
   [clojure.core.async :refer [chan go-loop close! <! >!]]))

(defn exec-with
  [in out inst]
  (go-loop [data {}
            d 0
            i 0]
    (if (< i (count inst))
      (case (nth inst i)
        
        \>
        ;; increment the data pointer (to point to the next cell to the right)
        (recur data (inc d) (inc i))
        
        \<
        ;; decrement the data pointer (to point to the next cell to the left)
        (recur data (dec d) (inc i))
        
        \+
        ;; increment (increase by one) the byte at the data pointer
        (let [v (get data d 0)]
          (recur (assoc data d (inc v)) d (inc i)))
        
        \-
        ;; decrement (decrease by one) the byte at the data pointer
        (let [v (get data d 0)]
          (recur (assoc data d (dec v)) d (inc i)))
        
        \[
        ;; if the byte at the data pointer is zero, then instead of moving the
        ;; instruction pointer forward to the next command, jump it forward to
        ;; the command after the matching "]" command.
        (if (zero? (get data d 0))
          (let [k (loop [nest 0
                         j (inc i)]
                    (case (nth inst j)
                      \]
                      (if (< 0 nest)
                        (recur (dec nest) (inc j))
                        j)
                      \[
                      (recur (inc nest) (inc j))
                      (recur nest (inc j))))]
            (recur data d (inc k)))
          (recur data d (inc i)))
        
        \]
        ;; if the byte at the data pointer is nonzero, then instead of moving the
        ;; instruction pointer forward to the next command, jump it back to
        ;; the command after the matching "[" command.
        (if (not (zero? (get data d 0)))
          (let [k (loop [nest 0
                         j (dec i)]
                    (case (nth inst j)
                      \[
                      (if (< 0 nest)
                        (recur (dec nest) (dec j))
                        j)
                      \]
                      (recur (inc nest) (dec j))
                      (recur nest (dec j))))]
            (recur data d (inc k)))
          (recur data d (inc i)))
        
        \.
        ;; output the byte at the data pointer
        (let [v (get data d 0)]
          (>! out v)
          (recur data d (inc i)))
        
        \,
        ;; accept one byte of input, storing its value in the byte at the data pointer
        (let [v (or (<! in) 0)]
          (recur (assoc data d v) d (inc i)))
        
        ;; for any other characters skip to next instruction
        (recur data d (inc i)))
      (close! out))))

(defn exec
  [in inst]
  (let [out (chan 1)]
    (exec-with in out inst)
    out))

(defmacro pipe
  [in inst & more]
  `(-> (exec ~in ~inst)
     ~@(for [i more] `(exec ~i))))





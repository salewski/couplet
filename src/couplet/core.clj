(ns couplet.core
  "Core utilities for working with Unicode code points."
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import [clojure.lang IReduce Sequential]
           java.io.Writer
           [java.util.concurrent ForkJoinPool ForkJoinTask]))

(defn codepoint?
  "Returns true if x is a code point.

  Corresponds to the spec :couplet.core/codepoint."
  [x]
  (and (int? x) (<= Character/MIN_CODE_POINT x Character/MAX_CODE_POINT)))

(defmacro codepoint-in
  "Returns a spec that validates (and generates) code points in the range from
  start to end inclusive.

  The predefined spec :couplet.core/codepoint validates all code points."
  [start end]
  `(s/spec #(and (int? %) (<= ~start % ~end))
     :gen #(gen/fmap int (gen/choose ~start ~end))))

(s/def ::codepoint
  (codepoint-in Character/MIN_CODE_POINT Character/MAX_CODE_POINT))

(defn codepoint-str
  "Returns a string containing the Unicode character specified by code point cp."
  [cp]
  (String/valueOf (Character/toChars cp)))

(defn- codepoint-xform
  [rf]
  (let [high (volatile! nil)]
    (fn
      ([] (rf))
      ([result]
       (rf (if-let [c @high]
             (unreduced (rf result (int c)))
             result)))
      ([result c]
       (if-let [c1 @high]
         (cond
           (Character/isLowSurrogate c)
           (do (vreset! high nil)
               (rf result (Character/toCodePoint c1 c)))
           (Character/isHighSurrogate c)
           (let [result (rf result (int c1))]
             (vreset! high (if (reduced? result) nil c))
             result)
           :else
           (do (vreset! high nil)
               (let [result (rf result (int c1))]
                 (if (reduced? result)
                   result
                   (rf result (int c))))))
         (if (Character/isHighSurrogate c)
           (do (vreset! high c)
               result)
           (rf result (int c))))))))

(defn- codepoint-reduce
  [^CharSequence s ^long i f val]
  (loop [i i
         ret val]
    (if (< i (.length s))
      (let [cp (Character/codePointAt s i)
            ret (f ret cp)]
        (if (reduced? ret)
          @ret
          (recur (+ i (if (Character/isBmpCodePoint cp) 1 2)) ret)))
      ret)))

(defn- fold-codepoints
  [^CharSequence s start end n combinef reducef]
  (if (or (<= (- end start) n)
          (and (= (- end start) 2)
               (Character/isHighSurrogate (.charAt s start))
               (Character/isLowSurrogate (.charAt s (inc start)))))
    (codepoint-reduce (.subSequence s start end) 0 reducef (combinef))
    (let [split (+ start (quot (- end start) 2))
          split (cond-> split
                  (and (Character/isHighSurrogate (.charAt s (dec split)))
                       (Character/isLowSurrogate (.charAt s split)))
                  inc)
          ^ForkJoinTask task
          (r/fjtask #(fold-codepoints s split end n combinef reducef))]
      (.fork task)
      (combinef (fold-codepoints s start split n combinef reducef)
                (.join task)))))

(deftype CodePointSeq [^CharSequence s]
  Iterable
  (iterator [_]
    (.iterator (.codePoints s)))

  Sequential

  IReduce
  (reduce [_ f]
    (case (.length s)
      0 (f)
      1 (int (.charAt s 0))
      (if-let [val (and (Character/isHighSurrogate (.charAt s 0))
                        (Character/isLowSurrogate (.charAt s 1))
                        (Character/toCodePoint (.charAt s 0) (.charAt s 1)))]
        (if (= (.length s) 2)
          val
          (codepoint-reduce s 2 f val))
        (codepoint-reduce s 1 f (int (.charAt s 0))))))
  (reduce [_ f val]
    (if (zero? (.length s))
      val
      (codepoint-reduce s 0 f val)))

  r/CollFold
  (coll-fold [_ n combinef reducef]
    (cond
      (zero? (.length s))
      (combinef)

      (<= (.length s) n)
      (codepoint-reduce s 0 reducef (combinef))

      :else
      (.invoke ^ForkJoinPool @r/pool
               (r/fjtask
                 #(fold-codepoints s 0 (.length s) n combinef reducef))))))

(defmethod print-method CodePointSeq
  [^CodePointSeq cps ^Writer w]
  (if *print-readably*
    (do (.write w "#couplet.core.CodePointSeq")
        (print-method (vector (str (.s cps))) w))
    (print-method (map codepoint-str cps) w)))

(defn codepoints
  "Returns a value that acts like a sequence of code points produced from the given
  CharSequence s. The result is of a type that is seqable, reducible, and
  foldable. The wrapped CharSequence is treated as immutable (like a string).

  Unlike CharSequence, the value returned from codepoints is not counted? and does
  not support random access. Use seq to obtain a regular (lazy) seq of code
  points.

  When no argument is supplied, returns a stateful transducer that transforms char
  inputs to code points."
  ([] codepoint-xform)
  ([s]
   {:pre [(instance? CharSequence s)]}
   (->CodePointSeq s)))

(defn append!
  "Reducing function applicable to code point input, with accumulation based on
  (mutable) StringBuilder. When called with no arguments, returns a new
  StringBuilder. When called with a StringBuilder argument, returns its contents
  as a string (for use in completion of transduce)."
  ([] (StringBuilder.))
  ([^StringBuilder sb] (str sb))
  ([^StringBuilder sb cp] (.appendCodePoint sb (int cp))))

(defn to-str
  "Returns a string containing the code points in coll. When a transducer is
  supplied, applies the transform to the inputs before appending them to the
  result.

  Same as (transduce xform append! coll), so coll must either directly or by way
  of transformation through xform consist of code points."
  ([coll]
   (to-str identity coll))
  ([xform coll]
   (transduce xform append! coll)))

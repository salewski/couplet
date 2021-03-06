# Couplet

Couplet is a small library that provides support for working with Unicode
characters or ‘code points’ in Clojure.

The distinguishing feature of this library is the type that represents a
sequence of code points: that type is efficiently seqable and reducible, and
also supports parallel fold via fork/join.

This library targets Clojure on the JVM.

[![Clojars Project](https://img.shields.io/clojars/v/ch.gluet/couplet.svg)](https://clojars.org/ch.gluet/couplet)
[![Build Status](https://travis-ci.org/glts/couplet.svg?branch=master)](https://travis-ci.org/glts/couplet)

## Requirements

*   Clojure 1.9
*   Java 8

## Dependency information

Clojure CLI tools:

```clojure
ch.gluet/couplet {:mvn/version "0.1.2"}
```

Leiningen/Boot:

```clojure
[ch.gluet/couplet "0.1.2"]
```

## Usage

Require the core namespace as `cp`, then use `cp/codepoints` to obtain a
seqable/reducible of code points.

```clojure
(require '[couplet.core :as cp])

(cp/codepoints "b🐝e🌻e")
;; => #couplet.core.CodePointSeq["b🐝e🌻e"]

(seq (cp/codepoints "b🐝e🌻e"))
;; => (98 128029 101 127803 101)

(->> "b🐝e🌻e" cp/codepoints (take-nth 2) cp/to-str)
;; => "bee"
```

## Documentation

*   [API documentation](https://glts.github.io/couplet/couplet.core.html)
*   [Walkthrough](https://github.com/glts/couplet/blob/master/example/walkthrough.clj)

## Design goals

*   *small*: provide basic building blocks for working with Unicode characters,
    not more
*   *efficient*: as performant as reasonably possible in Clojure on the JVM
*   *transparent*: allow processing any string, no well-formedness requirement
    imposed, no exceptions thrown nor mangling done on ill-formed UTF-16 input

## Related work

There are other solutions for the same problem, though perhaps written with
different goals in mind.

*   https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/string.clj
*   https://github.com/daveyarwood/djy
*   https://lambdaisland.com/blog/12-06-2017-clojure-gotchas-surrogate-pairs

Check out [ICU](http://site.icu-project.org/) for an extensive, mature Java
library for Unicode.

## Performance

Run the benchmarks with

```
lein jmh '{:type :quick, :format :table}'
```

The following is a short summary of the findings.

Broadly speaking, processing strings using code points instead of `char`s has no
negative impact on performance. On the contrary, the performance achieved here
compares favourably with that of Clojure’s own `char`-based string processing.

*   Reduce is faster than processing a lazy seq of code points by a factor of 3.
*   Parallel fold can be faster than reduce by a factor proportional to the
    number of cores.
*   Compared with Clojure strings, performance differences range from on par
    (reducing code points versus reducing a string) to faster by a factor of 3
    (`cp/to-str` versus `apply str`) to faster by a factor of 5 (lazy seq of
    code points versus lazy seq of `char`s).

Strings support fast random access – code point seqs do not. For efficient
lookup of code points by index consider a `vector-of :int` or Java array of
`int`.

## Licence

Copyright © 2017 David Bürgin

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.

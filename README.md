# bb-scripts

Development incubator for Babashka scripts. This is where I develop scripts in
Clojure and then run them with Babashka.

I gave a presentation about writing scripts in Babashka. Slides are [here](https://github.com/justone/bb-present).

# Using the scripts

If you only want to use one of the scripts, just download it out of the
[uberscripts](uberscripts/) directory and put it in a directory on your
`$PATH`.

Current scripts:

* [ftime](uberscripts/ftime) - Print out a human-readable time based on passing in millis.
* [penv](uberscripts/penv) - Prints out the environment like `env` does, but it masks variables that it thinks are private (like `SLACK_TOKEN`).
* [comb](uberscripts/comb) - Template data using [comb](https://github.com/weavejester/comb).

# Development Workflow

## Set up

Before you begin development, you should have the following installed on your PATH:

* [Babashka](https://github.com/borkdude/babashka/) as `bb`
* [Clojure](https://clojure.org/guides/getting_started) as `clojure`

## Creating a script

To create a script, you need to create two files. For instance, to create a new script called `foo`, create the following files:

`script/foo` - a Babashka dev runner
```
#!/usr/bin/env bash

cd $(dirname $0)/..

bb -cp $(clojure -Spath) -m foo -- "$@"
```

`src/foo.clj` - the Clojure source for the script
```
(ns foo)

(defn -main [& args]
  (println "foo"))
```


## Development

Start a repl with `clojure -A:clj:repl`. This will expose nREPL and pREPL ports
for editor integration. This allows for full iterative REPL-driven development.

To test running the script as a whole, use either of the following:

* `./script/foo [args]` - to test running in Babashka
* `clojure -A:clj -m foo [args]` - to test running in Clojure

## Uberscripting

Babashka can combine all namespaces used by a script into one file called an uberscript. There is a script in `./scripts/uberscriptify` that will combine this with the proper header. To create an uberscript, just run:

```
./scripts/uberscriptify -s foo
```

And the resulting file will be in `uberscripts/foo`.

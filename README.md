# bb-scripts

Development incubator for Babashka scripts. This is where I develop scripts in
Clojure and then run them with Babashka.

# Using the scripts

If you only want to use one of the scripts, just download it out of the
[uberscripts](uberscripts/) directory and put it in a directory on your
`$PATH`.

Current scripts:

* [ftime](uberscripts/ftime) - Print out a human-readable time based on passing in millis.
* [penv](uberscripts/penv) - Prints out the environment like `env` does, but it masks variables that it thinks are private (like `SLACK_TOKEN`).
* [comb](uberscripts/comb) - Template data using [comb](https://github.com/weavejester/comb).

# Development

## Set up

Before you begin development, you should have the following installed on your PATH:

* [Babashka](https://github.com/borkdude/babashka/) as `bb`
* [deps.clj](https://github.com/borkdude/deps.clj) as `deps.clj`

## Creating a script

WIP

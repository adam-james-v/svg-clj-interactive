# svg-clj-interactive
An interactive example App using [svg-clj](https://github.com/adam-james-v/svg-clj).

Try it out! [svg-clj-interactive](https://adam-james-v.github.io/svg-clj-interactive/index.html).


## TODO
This is meant to be a demo, but even so, there are a few things I hope to address:

- more efficient eval. Currently I re-eval the src after every change, which slows down with larger code blocks.
- clean up sci-eval context setup. I think there's a smarter way than hard-coding the namespace into each eval-string.
- make it work well on mobile. My iPad doesn't seem to support resizeable divs for example
- make the build process better. I think I may learn shadow-cljs and try to use that instead.
- try to use nextjournal's clojure-mode instead - they already have the killer features in their editor, I just need to learn how to use it.
- svg-clj has some bugs related to rotation calculations. Seems to be floating point precision issues that are different than in a clj context, but I don't know yet.
- add some documentation and examples so users know what functions are available.


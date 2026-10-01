# hawk.clojure

A Clojure client for the [Hawk](https://hawk.so) error catcher.

## Installation

Add a git dependency to your `deps.edn`:

```clojure
{:deps {io.github.krvital/hawk.clojure {:git/sha "<commit-sha>"}}}
```

## Usage

```clojure
(require '[hawk.core :as hawk])

(def client (hawk/make-client "put token here"))

(try
  (/ 1 0)
  (catch Exception e
    (hawk/send! client e)))
```

`send!` also accepts optional `context` and `user` maps:

```clojure
(hawk/send! client e {:some "context"} {:id "42" :name "Jane"})
```

### Ring middleware

`hawk.middleware/wrap-hawk` catches exceptions raised by a Ring handler, reports them to Hawk,
and re-raises them:

```clojure
(require '[hawk.middleware :as hawk.middleware])

(def app
  (hawk.middleware/wrap-hawk handler client))
```

## Development

Run the test suite:

```sh
clojure -X:test
```

Start an nREPL:

```sh
clojure -M:nrepl
```

## License

MIT, see [LICENSE](LICENSE).

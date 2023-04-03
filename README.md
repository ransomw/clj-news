# clj-news

aggregate data display in the browser

### status

as is, this project is mostly about understanding Clojure's new
[Deps and CLI](https://clojure.org/guides/deps_and_cli)
interface.  all it does is

* scrape HN and connect to the twitter api
* display the results in the browser

### usage

* copy the `resources/*.example` files to `resources/*` and fill in appropriate values
* `./run.sh` and `cider-connect`
* eval `styles.*` namespaces (`C-c C-k`) to create CSS
* `dev/go` starts the webserver
* `./build.sh` to start a figwheel build

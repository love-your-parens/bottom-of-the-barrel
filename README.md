# Bottom of the Barrel

A simple web scraping library for personal use. 

## Usage

Basic usage is decently auto-magical. The intended approach is to simply scrape everything, preferably leveraging cache to avoid generating needless traffic.

As such, the entire operation boils down to:

``` clojure
(require 'bottom-of-the-barrel.core)
```

And then:

``` clojure
(bottom-of-the-barrel.core/fetch-all)
```

Or better yet:

``` clojure
(bottom-of-the-barrel.core/fetch-all-with-cache!)
```

Cache expiry time is controlled by the *cache-expiry* variable. Here's how you can override it:

``` clojure
(binding [*cache-expiry* 60] ;60 seconds
 (fetch-all-with-cache!))
```

### Fetching from a single source

Namespaces responsible for scraping individual sources/endpoints all reside under `sources`. They are self-contained and can be used in separation. 

Per convention, every source should expose a zero-arity function `fetch`, returning a collection of data-points compliant with `::event` (see: spec).

> **Note:** Retrieving data in this manner offers no caching.

### Validating against the spec

Specs pertaining to the shape of scraped datapoints are available in `bottom-of-the-barrel.schema`, chief of them being `::event`. All sources are expected to yield collections of valid `events`. Note however that this won't be enforced at runtime.

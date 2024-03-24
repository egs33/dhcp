#!/bin/sh
mkdir -p classes
clojure -A:compile -M -e "(compile 'dhcp.core)"
clojure -M:uberjar --main-class dhcp.core

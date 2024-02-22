#!/bin/sh
mkdir -p classes
clojure -M -e "(compile 'dhcp.core)"
clojure -M:uberjar --main-class dhcp.core

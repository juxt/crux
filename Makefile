all: clean install build jar

clean:
	rm -rf target

install:
	yarn install

build:
	node_modules/.bin/shadow-cljs release app

build-perf:
	node_modules/.bin/shadow-cljs release app-perf

jar:
	lein with-profile base:crux-jars uberjar

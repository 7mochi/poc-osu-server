#!/usr/bin/env make

run:
	mvn spring-boot:run

run-caddy:
	caddy run --envfile .env --config ext/Caddyfile

lint:
	mvn spotless:apply




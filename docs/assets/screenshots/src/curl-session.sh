#!/bin/bash
# Runs each command for real and prints "$ cmd" followed by its real output.
# needs curl + jq on PATH and the app running on localhost:8080
run() { echo "\$ $1"; eval "$1"; }
run "TOKEN=\$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"alice\",\"password\":\"alice123\"}' | jq -r .accessToken)"
run "curl -s -X POST localhost:8080/api/articles -H \"Authorization: Bearer \$TOKEN\" -H 'Content-Type: application/json' -d '{\"title\":\"Hello LazyREST\",\"summary\":\"My first draft\",\"content\":\"Written with curl\"}' | jq -c '{id,title,status,author,version}'"
run "curl -s 'localhost:8080/api/articles?q=hello&size=5&sort=createdAt,desc' -H \"Authorization: Bearer \$TOKEN\" | jq -c '{totalElements, titles: [.content[].title]}'"
run "curl -s -X POST localhost:8080/api/articles -H \"Authorization: Bearer \$TOKEN\" -H 'Content-Type: application/json' -d '{\"title\":\"\"}' | jq ."
run "ADMIN=\$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin123\"}' | jq -r .accessToken)"
run "curl -s 'localhost:8080/api/audit-logs?actor=alice&size=3' -H \"Authorization: Bearer \$ADMIN\" | jq -c '.content[] | {action,outcome,channel,resourceId,durationMs}'"

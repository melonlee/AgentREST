#!/bin/bash
# needs curl + jq on PATH and the app running on localhost:8080
run() { echo "\$ $1"; eval "$1"; }
run "ADMIN=\$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin123\"}' | jq -r .accessToken)"
run "curl -s 'localhost:8080/api/audit-logs?channel=MCP&size=5' -H \"Authorization: Bearer \$ADMIN\" | jq -r '.content[] | [.occurredAt[11:19], .actor, .channel, .action, .resourceId, .outcome, .durationMs, .detail] | @tsv' | column -t -s \$'\\t'"
run "curl -s 'localhost:8080/api/audit-logs?channel=MCP&size=1' -H \"Authorization: Bearer \$ADMIN\" | jq '.content[0]'"

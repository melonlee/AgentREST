#!/bin/bash
# needs curl + jq on PATH and the app running on localhost:8080
run() { echo "\$ $1"; eval "$1" 2>/dev/null; }
run "npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http --header \"X-API-Key: \$AGENTREST_API_KEY\" --method tools/list | jq -c '.tools[] | {name, readOnlyHint: .annotations.readOnlyHint, params: (.inputSchema.properties | keys)}'"
run "npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http --header \"X-API-Key: \$AGENTREST_API_KEY\" --method tools/call --tool-name searchArticles --tool-arg query=mcp | jq -r '.content[0].text' | jq -c '.content[] | {id,title,status}'"

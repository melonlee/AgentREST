package com.fantasyailab.agentrest.mcp;

/** A tool failure whose message is safe and useful to show to the calling AI model. */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }
}

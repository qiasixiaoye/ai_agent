package com.vs.vsaiagent.demo.invoke;

/**
 * Demo-only API key holder.
 */
public interface TestApiKey {

    String API_KEY = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
}

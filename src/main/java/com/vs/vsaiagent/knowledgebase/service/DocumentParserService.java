package com.vs.vsaiagent.knowledgebase.service;

public interface DocumentParserService {
    String parseText(String fileName, byte[] bytes);
}

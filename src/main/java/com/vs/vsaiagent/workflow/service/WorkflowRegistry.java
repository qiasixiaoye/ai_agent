package com.vs.vsaiagent.workflow.service;

import com.vs.vsaiagent.workflow.WorkflowDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版工作流注册中心。MVP 阶段进程重启会丢；后续可接 PG。
 */
@Component
public class WorkflowRegistry {

    private final ConcurrentMap<String, WorkflowDef> store = new ConcurrentHashMap<>();

    public void save(WorkflowDef def) { store.put(def.id(), def); }

    public Optional<WorkflowDef> find(String id) { return Optional.ofNullable(store.get(id)); }

    public List<WorkflowDef> listAll() { return new ArrayList<>(store.values()); }

    public boolean remove(String id) { return store.remove(id) != null; }
}

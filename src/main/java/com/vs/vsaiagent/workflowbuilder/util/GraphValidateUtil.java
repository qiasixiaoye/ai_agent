package com.vs.vsaiagent.workflowbuilder.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图结构校验工具。
 */
public final class GraphValidateUtil {

    private GraphValidateUtil() {
    }

    /**
     * Kahn 拓扑排序判环。
     *
     * @param nodeIds 全部节点 id
     * @param edges   边列表，每条边为 [source, target]；指向不存在节点的边在调用前应已单独报错，这里忽略
     * @return true 表示存在环
     */
    public static boolean hasCycle(Set<String> nodeIds, List<String[]> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : nodeIds) {
            adjacency.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }
        for (String[] e : edges) {
            String source = e[0];
            String target = e[1];
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                continue;
            }
            adjacency.get(source).add(target);
            inDegree.merge(target, 1, Integer::sum);
        }

        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                queue.add(id);
            }
        });

        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : adjacency.get(current)) {
                int degree = inDegree.merge(next, -1, Integer::sum);
                if (degree == 0) {
                    queue.add(next);
                }
            }
        }
        return visited < nodeIds.size();
    }
}

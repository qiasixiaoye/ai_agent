package com.vs.vsaiagent.skill.registry;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class InMemorySkillRegistry implements SkillRegistry {

    private final ConcurrentMap<String, Skill> store = new ConcurrentHashMap<>();

    @Override
    public void register(Skill skill) {
        if (skill == null) return;
        SkillMetadata md = skill.metadata();
        if (md == null || md.name() == null || md.name().isBlank()) {
            log.warn("[skill-registry] reject skill without name: {}", skill.getClass());
            return;
        }
        Skill prev = store.put(md.name(), skill);
        if (prev == null) {
            log.info("[skill-registry] register skill={} version={} sourceType={}",
                    md.name(), md.version(), md.sourceType());
        } else {
            log.info("[skill-registry] replace skill={} oldClass={} newClass={}",
                    md.name(), prev.getClass().getSimpleName(), skill.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<Skill> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(store.get(name));
    }

    @Override
    public List<Skill> listAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Skill> listByTag(String tag) {
        if (tag == null || tag.isBlank()) return List.of();
        List<Skill> out = new ArrayList<>();
        for (Skill s : store.values()) {
            if (s.metadata().tags() != null && s.metadata().tags().contains(tag)) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public boolean unregister(String name) {
        return store.remove(name) != null;
    }
}

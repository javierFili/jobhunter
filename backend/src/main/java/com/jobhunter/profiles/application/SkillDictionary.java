package com.jobhunter.profiles.application;

import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.infrastructure.SkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic technology detection in free text (CVs, job descriptions) using the skills catalogue and its
 * aliases. Symbol-aware boundaries keep "Java" out of "JavaScript" and "C" out of "C++"/"C#".
 * Ambiguous very short names (C, R, Go) only match case-sensitively inside list-like contexts.
 */
@Component
public class SkillDictionary {

    private static final Set<String> AMBIGUOUS = Set.of("c", "r", "go");
    private static final String BEFORE = "(?<![\\p{L}\\p{N}+#./-])";
    private static final String AFTER = "(?![\\p{L}\\p{N}+#]|\\.[\\p{L}\\p{N}])";
    private static final String LIST_BEFORE = "(?<=(?:^|[,/|•·(;:]\\s{0,3}))";
    private static final String LIST_AFTER = "(?=\\s{0,3}(?:$|[,/|);]))";

    private final SkillRepository skills;
    private volatile List<Entry> entries;

    public SkillDictionary(SkillRepository skills) {
        this.skills = skills;
    }

    public record Match(Long skillId, String name, String matchedText) {
    }

    record Entry(Long skillId, String name, Pattern pattern) {
    }

    /** All distinct catalogue skills mentioned in the text, in order of first appearance. */
    @Transactional(readOnly = true)
    public List<Match> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<long[]> hits = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        for (Entry e : entries()) {
            Matcher m = e.pattern().matcher(text);
            if (m.find()) {
                hits.add(new long[]{m.start(), matches.size()});
                matches.add(new Match(e.skillId(), e.name(), m.group()));
            }
        }
        hits.sort(Comparator.comparingLong(h -> h[0]));
        Set<Long> seen = new LinkedHashSet<>();
        List<Match> ordered = new ArrayList<>();
        for (long[] h : hits) {
            Match match = matches.get((int) h[1]);
            if (seen.add(match.skillId())) {
                ordered.add(match);
            }
        }
        return ordered;
    }

    /** Call after the catalogue changes. */
    public void invalidate() {
        entries = null;
    }

    private List<Entry> entries() {
        List<Entry> local = entries;
        if (local == null) {
            synchronized (this) {
                local = entries;
                if (local == null) {
                    local = build(skills.findAll());
                    entries = local;
                }
            }
        }
        return local;
    }

    static List<Entry> build(List<Skill> catalogue) {
        List<Entry> result = new ArrayList<>();
        for (Skill s : catalogue) {
            List<String> terms = new ArrayList<>();
            terms.add(s.getName());
            terms.addAll(s.getAliases());
            List<String> alternatives = new ArrayList<>();
            boolean ambiguous = AMBIGUOUS.contains(s.getNormalizedName());
            for (String term : terms) {
                if (term == null || term.isBlank()) continue;
                String quoted = Pattern.quote(term.trim()).replace(" ", "\\E\\s+\\Q");
                if (ambiguous && term.equalsIgnoreCase(s.getName())) {
                    alternatives.add("(?-i:" + LIST_BEFORE + quoted + LIST_AFTER + ")");
                } else {
                    alternatives.add(BEFORE + quoted + AFTER);
                }
            }
            if (!alternatives.isEmpty()) {
                result.add(new Entry(s.getId(), s.getName(),
                        Pattern.compile("(?:" + String.join("|", alternatives) + ")",
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE)));
            }
        }
        return result;
    }
}

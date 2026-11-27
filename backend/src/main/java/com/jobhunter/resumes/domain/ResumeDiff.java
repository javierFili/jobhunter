package com.jobhunter.resumes.domain;

import java.util.ArrayList;
import java.util.List;

/** Line-level comparison of two resume documents (LCS), grouped by section for the comparison UI. */
public final class ResumeDiff {

    public enum Op { SAME, ADDED, REMOVED }

    public record Line(String section, String text) {
    }

    public record Change(Op op, String section, String text) {
    }

    private ResumeDiff() {
    }

    public static List<Change> diff(ResumeDocument from, ResumeDocument to) {
        return diff(flatten(from), flatten(to));
    }

    static List<Change> diff(List<Line> a, List<Line> b) {
        int n = a.size();
        int m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(i).equals(b.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Change> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(new Change(Op.SAME, a.get(i).section(), a.get(i).text()));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new Change(Op.REMOVED, a.get(i).section(), a.get(i).text()));
                i++;
            } else {
                out.add(new Change(Op.ADDED, b.get(j).section(), b.get(j).text()));
                j++;
            }
        }
        while (i < n) out.add(new Change(Op.REMOVED, a.get(i).section(), a.get(i++).text()));
        while (j < m) out.add(new Change(Op.ADDED, b.get(j).section(), b.get(j++).text()));
        return out;
    }

    public static List<Line> flatten(ResumeDocument d) {
        List<Line> lines = new ArrayList<>();
        if (d.basics() != null) {
            add(lines, "basics", d.basics().fullName());
            add(lines, "basics", d.basics().headline());
        }
        add(lines, "summary", d.summary());
        for (var e : d.experience()) {
            add(lines, "experience", e.title() + " — " + e.company() + " (" + e.startDate() + " – "
                    + (e.current() ? "…" : e.endDate()) + ")");
            if (e.bullets() != null) e.bullets().forEach(bl -> add(lines, "experience", "• " + bl.text()));
        }
        for (var p : d.projects()) {
            add(lines, "projects", p.name() + (p.role() == null ? "" : " — " + p.role()));
            if (p.bullets() != null) p.bullets().forEach(bl -> add(lines, "projects", "• " + bl.text()));
        }
        d.education().forEach(e -> add(lines, "education", e.institution() + (e.degree() == null ? "" : " — " + e.degree())));
        d.certifications().forEach(c -> add(lines, "certifications", c.name() + (c.issuer() == null ? "" : " — " + c.issuer())));
        d.skills().forEach(g -> add(lines, "skills", g.label() + ": " + String.join(", ",
                g.items() == null ? List.of() : g.items().stream().map(ResumeDocument.SkillEntry::name).toList())));
        d.languages().forEach(l -> add(lines, "languages", l.code() + " " + (l.level() == null ? "" : l.level())));
        return lines;
    }

    private static void add(List<Line> lines, String section, String text) {
        if (text != null && !text.isBlank()) {
            for (String part : text.split("\n")) {
                if (!part.isBlank()) lines.add(new Line(section, part.trim()));
            }
        }
    }
}

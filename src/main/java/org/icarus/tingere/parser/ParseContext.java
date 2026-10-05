package org.icarus.tingere.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 鉴于先前的 commit 把 context 炸掉了所以根据芒果的思路重做了一个
 */
public final class ParseContext {

    private final String label;
    private final String file;
    private final Consumer<ParseProblem> sink;
    private final Map<String, ParseProblem> problems = new LinkedHashMap<>();

    public ParseContext(String label, String file, Consumer<ParseProblem> sink) {
        this.label = label;
        this.file = file;
        this.sink = sink;
    }

    public ParseContext(String label, String file) {
        this(label, file, null);
    }

    public <P extends ParseProblem> P err(P problem) {
        this.problems.putIfAbsent(key(problem), problem);
        return problem;
    }

    public ParseProblem.Component componentError(String subject, String detail) {
        return err(new ParseProblem.Component(subject, detail));
    }

    public int errorCount() {
        return this.problems.size();
    }

    public boolean hasErrors() {
        return !this.problems.isEmpty();
    }

    public List<ParseProblem> problems() {
        return List.copyOf(this.problems.values());
    }

    // 不下传 logger 只下传 logger::warn
    public void report(Consumer<ParseProblem> consumer) {
        this.problems.values().forEach(consumer);
    }

    public void report() {
        if (this.sink != null) {
            report(this.sink);
        }
    }

    public String where() {
        return this.file == null ? this.label : this.file + " / " + this.label;
    }

    public <T> ParseResult<T> ok(T value) {
        return new ParseResult<>(this, value, problems());
    }

    private static String key(ParseProblem problem) {
        return problem.getClass().getSimpleName() + '\u0000' + problem.subject();
    }
}

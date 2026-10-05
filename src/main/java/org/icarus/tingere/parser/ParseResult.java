package org.icarus.tingere.parser;

import java.util.List;
import java.util.function.Function;

/**
 * 根据芒果的 ParseResult 思路来的
 * 没好事就对了。
 */
public record ParseResult<T>(ParseContext context, T value, List<ParseProblem> problems) {

    public T unwrap() {
        this.context.report();
        return this.value;
    }

    public T orElseThrow() {
        if (!this.problems.isEmpty()) {
            throw this.problems.getFirst();
        }
        return this.value;
    }

    public <R> ParseResult<R> map(Function<T, R> mapper) {
        return new ParseResult<>(this.context, this.value == null ? null : mapper.apply(this.value), this.problems);
    }
}

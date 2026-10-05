package org.icarus.tingere.parser;

public sealed class ParseProblem extends RuntimeException
        permits ParseProblem.Structure, ParseProblem.Component {

    private final String subject;

    private ParseProblem(String subject, String detail, Throwable cause) {
        super(detail, cause);
        this.subject = subject;
    }

    public String subject() {
        return this.subject;
    }

    public String describe() {
        return this.subject == null ? getMessage() : '\'' + this.subject + "': " + getMessage();
    }

    /**
     * 结构性异常。e.g. 两个 registry
     */
    public static final class Structure extends ParseProblem {
        public Structure(String subject, String detail) {
            super(subject, detail, null);
        }
    }

    /**
     * 物品组件异常。e.g. 一个不存在的组件名 "gunmu"
     */
    public static final class Component extends ParseProblem {
        public Component(String subject, String detail) {
            super(subject, detail, null);
        }
    }
}

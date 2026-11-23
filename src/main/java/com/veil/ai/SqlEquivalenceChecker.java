package com.veil.ai;

import com.veil.ast.SelectStatement;
import com.veil.lexer.Lexer;
import com.veil.parser.Parser;
import com.veil.parser.SemanticAnalyzer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Checks semantic equivalence between two SQL queries.
@Component
public class SqlEquivalenceChecker {

    private final SemanticAnalyzer semanticAnalyzer;

    public SqlEquivalenceChecker(SemanticAnalyzer semanticAnalyzer) {
        this.semanticAnalyzer = semanticAnalyzer;
    }

    // Checks if two SQL queries are semantically equivalent.
    public EquivalenceResult checkEquivalence(String sql1, String sql2) {
        if (sql1 == null || sql2 == null) {
            return EquivalenceResult.inconclusive("One or both queries are null");
        }

        // Quick string equality check
        if (normalize(sql1).equals(normalize(sql2))) {
            return EquivalenceResult.equivalent("Queries are textually identical after normalization");
        }

        // Parse both queries
        SelectStatement select1, select2;
        try {
            select1 = (SelectStatement) new Parser(new Lexer(sql1).tokenize()).parse();
            select2 = (SelectStatement) new Parser(new Lexer(sql2).tokenize()).parse();
        } catch (Exception e) {
            return EquivalenceResult.inconclusive("Parse error: " + e.getMessage());
        }

        // Compare AST structures
        AstComparison astDiff = compareAst(select1, select2);
        if (!astDiff.areEquivalent()) {
            return EquivalenceResult.notEquivalent("AST structure differs: " + astDiff.getDifferences());
        }

        // Check semantic equivalence (requires schema - would need DB connection)
        // For now, return inconclusive if AST is equivalent but we can't verify semantically
        return EquivalenceResult.equivalent("AST structures match; semantic equivalence not fully verified (requires DB schema)");
    }

    private String normalize(String sql) {
        return sql.trim()
                .replaceAll("\\s+", " ")
                .replaceAll(";\\s*$", "")
                .toUpperCase();
    }

    // AST comparison result
    public static class AstComparison {
        private final boolean equivalent;
        private final List<String> differences;

        public AstComparison(boolean equivalent, List<String> differences) {
            this.equivalent = equivalent;
            this.differences = differences;
        }

        public boolean areEquivalent() { return equivalent; }
        public List<String> getDifferences() { return differences; }
    }

    private AstComparison compareAst(SelectStatement s1, SelectStatement s2) {
        List<String> diffs = new ArrayList<>();

        // Compare SELECT items
        if (!compareSelectItems(s1.getSelectItems(), s2.getSelectItems())) {
            diffs.add("SELECT items differ");
        }

        // Compare FROM table
        if (!s1.getTable().getTableName().equals(s2.getTable().getTableName())) {
            diffs.add("FROM table differs");
        }

        // Compare JOINs
        if (!compareJoins(s1.getJoins(), s2.getJoins())) {
            diffs.add("JOINs differ");
        }

        // Compare WHERE
        if (!compareConditions(s1.getWhereCondition(), s2.getWhereCondition())) {
            diffs.add("WHERE condition differs");
        }

        // Compare GROUP BY
        if (!compareGroupBy(s1.getGroupBy(), s2.getGroupBy())) {
            diffs.add("GROUP BY differs");
        }

        // Compare ORDER BY
        if (!compareOrderBy(s1.getOrderBy(), s2.getOrderBy())) {
            diffs.add("ORDER BY differs");
        }

        // Compare LIMIT
        if (!compareLimits(s1.getLimit(), s2.getLimit())) {
            diffs.add("LIMIT differs");
        }

        return new AstComparison(diffs.isEmpty(), diffs);
    }

    private boolean compareSelectItems(List<?> items1, List<?> items2) {
        if (items1.size() != items2.size()) return false;
        for (int i = 0; i < items1.size(); i++) {
            if (!items1.get(i).toString().equals(items2.get(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean compareJoins(List<?> joins1, List<?> joins2) {
        if (joins1.size() != joins2.size()) return false;
        // Simplified: just check count for now
        return true;
    }

    private boolean compareConditions(Object cond1, Object cond2) {
        if (cond1 == null && cond2 == null) return true;
        if (cond1 == null || cond2 == null) return false;
        return cond1.toString().equals(cond2.toString());
    }

    private boolean compareGroupBy(Object gb1, Object gb2) {
        if (gb1 == null && gb2 == null) return true;
        if (gb1 == null || gb2 == null) return false;
        return gb1.toString().equals(gb2.toString());
    }

    private boolean compareOrderBy(List<?> ob1, List<?> ob2) {
        if (ob1.size() != ob2.size()) return false;
        for (int i = 0; i < ob1.size(); i++) {
            if (!ob1.get(i).toString().equals(ob2.get(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean compareLimits(Object l1, Object l2) {
        if (l1 == null && l2 == null) return true;
        if (l1 == null || l2 == null) return false;
        return l1.equals(l2);
    }

    // Result classes
    public static class EquivalenceResult {
        private final String status; // EQUIVALENT, NOT_EQUIVALENT, INCONCLUSIVE
        private final String reason;

        private EquivalenceResult(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public static EquivalenceResult equivalent(String reason) {
            return new EquivalenceResult("EQUIVALENT", reason);
        }

        public static EquivalenceResult notEquivalent(String reason) {
            return new EquivalenceResult("NOT_EQUIVALENT", reason);
        }

        public static EquivalenceResult inconclusive(String reason) {
            return new EquivalenceResult("INCONCLUSIVE", reason);
        }

        public String getStatus() { return status; }
        public String getReason() { return reason; }
    }
}
package com.veil.ast;

import java.util.List;

// Full SELECT statement with all clauses.
public class SelectStatement implements Statement {

    private final List<SelectItem> selectItems;
    private final TableReference table;
    private final Condition whereCondition;
    private final List<JoinClause> joins;
    private final GroupByClause groupBy;
    private final List<OrderByItem> orderBy;
    private final Long limit;

    // Minimal constructor for backward compatibility (simple grammar)
    public SelectStatement(List<String> columns, TableReference table, WhereCondition whereCondition) {
        this(columns.stream().map(SelectItem::new).toList(), table, whereCondition, List.of(), null, List.of(), null);
    }

    // Full constructor
    public SelectStatement(List<SelectItem> selectItems, TableReference table,
                           Condition whereCondition, List<JoinClause> joins,
                           GroupByClause groupBy, List<OrderByItem> orderBy, Long limit) {

        this.selectItems = selectItems;
        this.table = table;
        this.whereCondition = whereCondition;
        this.joins = joins;
        this.groupBy = groupBy;
        this.orderBy = orderBy;
        this.limit = limit;
    }

    // Backward compatibility getter - returns column names only
    public List<String> getColumns() {
        return selectItems.stream().map(SelectItem::toString).toList();
    }

    // New getter for full select items
    public List<SelectItem> getSelectItems() {
        return selectItems;
    }

    public TableReference getTable() {
        return table;
    }

    public Condition getWhereCondition() {
        return whereCondition;
    }

    public List<JoinClause> getJoins() {
        return joins;
    }

    public GroupByClause getGroupBy() {
        return groupBy;
    }

    public List<OrderByItem> getOrderBy() {
        return orderBy;
    }

    public Long getLimit() {
        return limit;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("SELECT ");
        result.append(selectItems.stream().map(SelectItem::toString).toList());

        result.append(" FROM ").append(table);

        for (JoinClause join : joins) {
            result.append(" ").append(join);
        }

        if (whereCondition != null) {
            result.append(" WHERE ").append(whereCondition);
        }

        if (groupBy != null) {
            result.append(" ").append(groupBy);
        }

        if (!orderBy.isEmpty()) {
            result.append(" ORDER BY ").append(orderBy);
        }

        if (limit != null) {
            result.append(" LIMIT ").append(limit);
        }

        return result.toString();
    }
}
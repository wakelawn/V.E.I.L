package com.veil.analysis;

import java.util.List;

// answers one question for index analysis: which columns does a table's
// indexes lead with? A column that is the FIRST column of some index is
// considered covered (see index-analysis.md - leading column rule)
public interface IndexInspector {

	List<String> leadingIndexedColumns(String tableName);
}

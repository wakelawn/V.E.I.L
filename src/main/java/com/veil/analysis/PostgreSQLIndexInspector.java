package com.veil.analysis;

import com.veil.core.database.DatabaseAdapter;
import com.veil.core.database.DatabaseCapabilities;

import org.springframework.stereotype.Component;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// PostgreSQL implementation: reads pg_indexes through the DatabaseAdapter
// and extracts the leading column of each index from its definition.
// Definitions look like:
//   CREATE UNIQUE INDEX pkey ON public.projects USING btree (id)
//   CREATE INDEX idx ON public.queries USING btree (project_id, status)
@Component
public class PostgreSQLIndexInspector implements IndexInspector {

	private final DatabaseAdapter adapter;

	public PostgreSQLIndexInspector(DatabaseAdapter adapter) {
		this.adapter = adapter;
	}

	@Override
	public List<String> leadingIndexedColumns(String tableName) {

		List<String> leading = new ArrayList<>();

		// degrade gracefully: if index support is missing or the database
		// is unreachable, report "nothing covered" and let the caller
		// decide (IndexAdvisor keeps its hint - honest over silent)
		DatabaseCapabilities capabilities = adapter.getCapabilities();

		if (!capabilities.isSupportsIndexes()) {
			return leading;
		}

		for (Map<String, Object> row : adapter.getIndexesForTable(tableName)) {

			String definition = String.valueOf(row.get("indexdef"));

			String firstColumn = leadingColumnOf(definition);

			if (firstColumn != null && !leading.contains(firstColumn)) {
				leading.add(firstColumn);
			}
		}

		return leading;
	}

	// "CREATE INDEX x ON public.t USING btree (project_id, status)"
	//  -> everything after the last '(' is the column list;
	//     the first column there is the leading column
	// package-private so the unit tests can check the parsing directly
	static String leadingColumnOf(String indexdef) {

		int open = indexdef.lastIndexOf('(');
		int close = indexdef.lastIndexOf(')');

		if (open < 0 || close <= open) {
			return null;
		}

		String columnList = indexdef.substring(open + 1, close).trim();

		if (columnList.isEmpty()) {
			return null;
		}

		// entries can be "col", "col DESC", "col COLLATE x" -
		// the column name is the first token, quotes removed
		String first = columnList.split(",")[0].trim();

		String[] parts = first.split("\\s+");

		return parts[0].replace("\"", "");
	}
}

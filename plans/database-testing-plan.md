# Database Integration Testing Plan (Test #2)

## Two-Level Testing Strategy (Based on Discussion)

### Level 1: Pure Function Tests (Query Construction)
**Fast, lightweight tests for SQL generation functions:**

**Pure Query Functions to Test:**
- `add-capture-query` - INSERT query construction
- `add-line-query` - INSERT query construction
- `find-captures-query` - Complex SELECT with JOIN and filtering
- `get-capture-query` - Simple SELECT by ID
- `set-comment-query` - UPDATE query construction
- `find-lines-query` - SELECT with ordering and limit

**Test Approach:**
- Test SQL string generation correctness
- Verify parameter binding
- Test query variations (with/without filters, limits, etc.)
- No database needed - pure function testing

### Level 2: Impure Function Tests (Database Integration)
**Full database integration tests with fresh SQLite DB per test:**

**Impure Functions to Test:**
- `init` - Database/table creation and migrations
- `add-capture` + `add-line` - Data insertion and ID generation
- `find-captures` - Query execution with filtering/joining
- `get-capture` - Single record retrieval
- `set-comment` - Update operations
- `get-lines` - Line retrieval with ordering
- `decode-captures` + `parse-sqlite-date` - Data transformation

## Test Database Fixture Strategy

### Database Per Test Approach
```clojure
(defn fresh-test-db []
  (let [temp-file (str (fs/create-temp-file {:suffix ".db"}))]
    (init {:db/location temp-file})
    {:db/location temp-file}))

(defn cleanup-test-db [config]
  (fs/delete (:db/location config)))
```

### Test Structure Pattern
1. **Setup**: Create fresh SQLite database with `init`
2. **Populate**: Add test data through functions
3. **Execute**: Run the function being tested
4. **Assert**: Verify database state/returned data
5. **Cleanup**: Delete database file

## Scenario-Based Test Categories

### A. CRUD Operations Scenarios
- **Create capture**: Verify ID generation, data storage
- **Add lines to capture**: Test foreign key relationships
- **Read captures**: Test filtering (session, directory, limits)
- **Update comments**: Verify updates don't affect other data
- **Complex queries**: JOIN operations for line counts

### B. Data Transformation Scenarios
- **Date parsing**: SQLite timestamps → ZonedDateTime
- **JSON attributes**: Roundtrip testing of attributes field
- **Data decoding**: Database records → domain objects

### C. Migration/Schema Scenarios
- **Fresh database**: Init creates all tables correctly
- **Existing database**: Init is idempotent, adds missing columns
- **Schema evolution**: Test comment column addition logic

### D. Edge Cases & Error Scenarios
- **Empty results**: No captures/lines found
- **Large datasets**: Performance with many captures/lines
- **Concurrent access**: WAL mode behavior
- **Invalid data**: Malformed JSON, missing foreign keys

## Implementation Plan

### Phase 1: Test Infrastructure (1 hour)
- Create `test/cap_db_test.clj`
- Implement database fixture functions
- Add test utilities for data creation

### Phase 2: Pure Function Tests (2 hours)
- Test all 6 query construction functions
- Focus on SQL correctness and parameter handling
- Lightweight, fast-running tests

### Phase 3: Integration Test Scenarios (4-5 hours)
- Implement 15-20 scenario-based tests
- Cover CRUD operations, data transformation, migrations
- Each test uses fresh database instance

### Phase 4: Edge Cases (1-2 hours)
- Error conditions, empty results, large data
- Performance validation for reasonable dataset sizes

## Expected Benefits
- **Data integrity assurance**: Catch SQL bugs before production
- **Migration safety**: Ensure schema changes work correctly
- **Regression prevention**: Detect breaking changes in database layer
- **Fast feedback**: SQLite tests run quickly in build pipeline
- **Confidence**: Full database roundtrip testing

## Integration with Build
- Tests run with `bb test` alongside existing tests
- Fresh database per test ensures isolation
- Fast enough for continuous integration
- Option to run specific test namespaces during development

This approach gives you comprehensive database testing while maintaining fast, reliable test execution through SQLite's lightweight nature and the fresh-database-per-test strategy.

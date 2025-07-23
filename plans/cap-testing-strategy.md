# Cap Testing Strategy & Cost/Benefit Analysis

## Project Overview

**Cap** is a shell output capture and retrieval system built with Clojure/Babashka. It allows users to:

1. **Capture command output** (`add`) - streams stdin to both stdout and SQLite storage
2. **Retrieve captures** (`get`) - fetch previous captures by ID, interactively, or most recent
3. **List captures** (`list`) - display captures with metadata (time, directory, session, line count)
4. **Add comments** (`comment`) - annotate captures for organization
5. **Session management** (`shell-init`) - track captures across shell sessions

The architecture uses:
- **SQLite database** with `captures` and `lines` tables
- **Session-aware filtering** via `CAP_SESSION_ID` environment variable
- **Directory-aware filtering** for workspace-specific captures
- **Interactive selection** using `fzf` for browsing captures

## Testing Strategy

### 1. Core Function Tests (`test/cap_test.clj`)

**Scope**: Pure function testing for utility functions in `cap.clj`

**Tests Implemented**:
- ✅ **Duration formatting** (`format-duration` cap.clj:27-40) - **COMPLETED**
  - ✅ 25 comprehensive test assertions covering all code paths
  - ✅ Boundary conditions (59m59s→1h0m, 23h59m→1d0h, 6d23h→7d)
  - ✅ Edge cases (zero values, milliseconds, large durations)
  - ✅ Real-world scenarios (common command durations)
  - ✅ Helper function `duration-from` for easy test setup

**Tests Remaining**:
- **Time calculations** (`calculate-ago` cap.clj:42-44)
  - Various time differences (seconds, minutes, hours, days)
  - Timezone handling consistency
- **Shell initialization** (`shell-init-str` cap.clj:46-48)
  - UUID format validation
  - Export syntax correctness
- **Line formatting** (`simple-line` cap.clj:50-52)
  - Output format consistency
  - Handling of edge cases (long paths, special characters)

**Cost**: **Low** (1-2 hours remaining)
- Simple pure functions
- No external dependencies
- Fast execution

**Benefit**: **High**
- ✅ Duration formatting regression prevention secured
- Easy to maintain
- High confidence in utility functions
- Quick feedback loop

**Priority**: **Essential** - 40% complete, remaining functions are core user-facing.

### 2. Database Integration Tests (`test/cap_db_test.clj`)

**Scope**: Testing database operations in `cap/db.cljc`

**Tests to Implement**:
- **CRUD operations**
  - Create captures with various attributes
  - Add lines to captures
  - Retrieve captures and lines
  - Update capture comments
- **Query filtering**
  - Session-based filtering
  - Directory-based filtering
  - Limit parameter handling
  - Combined filter scenarios
- **Date parsing** (`parse-sqlite-date` cap/db.cljc:86-91)
  - SQLite timestamp conversion accuracy
  - Timezone handling
- **Schema migrations**
  - Test `init` function idempotency
  - Verify table creation and alterations
  - Test migration from empty database

**Cost**: **Medium** (6-8 hours)
- Requires SQLite test setup/teardown
- Multiple test scenarios
- Need temporary database management

**Benefit**: **Critical**
- Prevents data corruption/loss
- Ensures data integrity across schema changes
- Critical for user trust (data persistence)
- Catches SQL generation bugs

**Priority**: **Essential** - Data integrity is paramount for a capture tool.

### 3. CLI Integration Tests (`test/cap_integration_test.clj`)

**Scope**: End-to-end testing of command-line interface and main entry points

**Tests to Implement**:
- **Command parsing**
  - All subcommands (`add`, `get`, `list`, `comment`, `init`, `shell-init`)
  - Flag combinations and validation
  - Argument parsing edge cases
- **Database location handling**
  - XDG path resolution
  - Custom DB paths via `--db` flag
  - Directory creation when needed
- **Error scenarios**
  - Missing database file
  - Invalid capture IDs
  - Malformed input
  - Permission errors
- **Environment integration**
  - `PWD` and `CAP_SESSION_ID` handling
  - XDG environment variables

**Cost**: **High** (8-12 hours)
- Complex setup (mocking environments, processes)
- Multiple execution paths
- Error case handling
- Process interaction testing

**Benefit**: **High**
- Ensures real-world usage works
- Catches integration bugs
- Validates user experience end-to-end
- Prevents breaking changes to CLI interface

**Priority**: **High** - Critical for user experience, but can be built incrementally.

### 4. Interactive Feature Tests (`test/cap_interactive_test.clj`)

**Scope**: Testing interactive features and external process integration

**Tests to Implement**:
- **Mock `fzf` process**
  - Test interactive picker without requiring fzf installation
  - Simulate user selections and cancellations
  - Test preview command generation
- **Capture finding logic**
  - Test `find-capture` with different option combinations
  - Interactive vs non-interactive modes
  - Fallback behavior when no captures found

**Cost**: **Medium-High** (6-10 hours)
- Complex mocking of external processes
- Process interaction simulation
- Platform-specific behavior testing

**Benefit**: **Medium**
- Ensures interactive features work correctly
- Catches process integration bugs
- But failures are often user-recoverable
- Less critical than data integrity

**Priority**: **Medium** - Important for UX but not blocking core functionality.

### 5. Property-Based & Edge Case Tests (`test/cap_property_test.clj`)

**Scope**: Advanced testing for edge cases and data validation

**Tests to Implement**:
- **Property-based tests for duration formatting**
  - Generate random durations and verify format consistency
  - Test roundtrip properties where applicable
- **Unicode and special character handling**
  - Test with various input encodings
  - Special characters in capture names, directories
- **Large data scenarios**
  - Many captures in database
  - Very long lines
  - Performance under load

**Cost**: **Medium** (4-6 hours)
- Requires test.check or similar library
- More complex test setup
- Longer execution times

**Benefit**: **Medium-Low**
- Catches edge cases missed by example-based tests
- Builds confidence in robustness
- But most edge cases are non-critical

**Priority**: **Low** - Nice to have, implement after core tests are solid.

## Test Infrastructure Recommendations

### Setup Requirements
- Use `babashka.fs/create-temp-dir` for isolated test databases
- Mock environment variables (`PWD`, `CAP_SESSION_ID`, `XDG_*`)
- Test both Babashka and Clojure JVM execution paths (where applicable)
- Implement test utilities for database setup/teardown

### Execution Strategy
1. **Start with Core Function Tests** - Quick wins, build confidence
2. **Add Database Integration Tests** - Critical for data safety
3. **Implement CLI Integration Tests** - Ensure user experience
4. **Add Interactive Feature Tests** - Polish the UX
5. **Consider Property-Based Tests** - Robustness improvement

### Total Estimated Effort
- **Essential tests (1-3)**: 14-22 hours (✅ saved 2 hours with duration tests complete)
- **Complete coverage (1-5)**: 24-38 hours

### ROI Analysis
- **Highest ROI**: Database Integration Tests (critical data safety)
- ✅ **Best quick wins**: Core Function Tests (duration formatting complete, high confidence achieved)
- **User experience**: CLI Integration Tests (prevents user frustration)
- **Polish**: Interactive and Property-based tests (nice to have)

## Implementation Priority

1. **Phase 1 (Essential)**: Core Functions + Database Integration (6-10 hours remaining)
   - ✅ Duration formatting tests complete
   - Remaining: 3 core functions + database integration
2. **Phase 2 (Important)**: CLI Integration (8-12 hours)
3. **Phase 3 (Polish)**: Interactive + Property-based (10-16 hours)

This strategy ensures you have solid test coverage for the most critical functionality while providing a clear path for comprehensive testing as the project grows.

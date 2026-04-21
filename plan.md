1. **Define `NewsQuery` data class**:
   - Add `data class NewsQuery` to `DashboardNewsRepository.kt` or a separate file if needed.
   - It should contain properties: `skip: Int`, `bookmark: String?`, `limit: Int`, `createdOn: String?`, `parentCode: String?`, `teamName: String? = null`.

2. **Update `fetchNews` signature in `DashboardNewsRepository.kt`**:
   - Replace the corresponding parameters in `fetchNews` with a single `query: NewsQuery` parameter.
   - Update the references to these parameters within `fetchNews` (e.g., `query.skip`, `query.bookmark`, `query.limit`, etc.).

3. **Update `fetchNews` calls in `DashboardVoicesFragment.kt`**:
   - Change the `fetchNews` call inside `loadMore` to pass a `NewsQuery` instance.

4. **Update `fetchNews` calls in `DashboardNewsRepositoryTest.kt`**:
   - Change all test calls to `fetchNews` to pass a `NewsQuery` instance.

5. **Pre-commit checks**:
   - Run verification and test steps.

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

/** Base class representing an SQL query that returns a [ResultSet] when executed. */
abstract class AbstractQuery<T : AbstractQuery<T>>(
    targets: List<Table>
) : SizedIterable<ResultRow>, Statement<ResultSet>(StatementType.SELECT, targets) {
    protected val transaction
        get() = TransactionManager.current()

    /** The stored list of columns and their [SortOrder] for an `ORDER BY` clause in this query. */
    var orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()
        private set

    /** The stored value for a `LIMIT` clause in this query. */
    var limit: Int? = null
        protected set

    /** The stored value for an `OFFSET` clause in this query. */
    var offset: Long = 0
        private set

    /** The number of results that should be fetched when this query is executed. */
    var fetchSize: Int? = null
        private set

    /** The set of columns on which a query should be executed, contained by a [ColumnSet]. */
    abstract val set: FieldSet

    /**
     * Copies all stored properties of this `SELECT` query into the properties of [other].
     *
     * Override this function to additionally copy any properties that are not part of the primary constructor.
     */
    open fun copyTo(other: T) {
        other.orderByExpressions = orderByExpressions.toMutableList()
        other.limit = limit
        other.offset = offset
        other.fetchSize = fetchSize
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean) = prepareSQL(QueryBuilder(prepared))

    /** Returns the string representation of an SQL query, generated by appending SQL expressions to a [QueryBuilder]. **/
    abstract fun prepareSQL(builder: QueryBuilder): String

    override fun arguments() = QueryBuilder(true).let {
        prepareSQL(it)
        if (it.args.isNotEmpty()) listOf(it.args) else emptyList()
    }

    /** Modifies this query to retrieve only distinct results if [value] is set to `true`. */
    abstract fun withDistinct(value: Boolean = true): T

    @Deprecated(
        "This function will be removed in future releases.",
        ReplaceWith("limit(n).offset(offset)"),
        DeprecationLevel.ERROR
    )
    override fun limit(n: Int, offset: Long): T = apply {
        limit = n
        this.offset = offset
    } as T

    /** Modifies this query to return only [count] results. **/
    override fun limit(count: Int): T = apply { limit = count } as T

    /** Modifies this query to return only results starting after the specified [start]. **/
    override fun offset(start: Long): T = apply { offset = start } as T

    /** Modifies this query to sort results by the specified [column], according to the provided [order]. **/
    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): T = orderBy(column to order)

    /** Modifies this query to sort results according to the provided [order] of expressions. **/
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): T = apply {
        (orderByExpressions as MutableList).addAll(order)
    } as T

    /** Modifies the number of results that should be fetched when this query is executed. */
    fun fetchSize(n: Int): T = apply {
        fetchSize = n
    } as T

    protected var count: Boolean = false

    protected abstract val queryToExecute: Statement<ResultSet>

    override fun iterator(): Iterator<ResultRow> {
        val resultIterator = ResultIterator(transaction.exec(queryToExecute)!!)
        return if (transaction.db.supportsMultipleResultSets) {
            resultIterator
        } else {
            Iterable { resultIterator }.toList().iterator()
        }
    }

    private inner class ResultIterator(val rs: ResultSet) : Iterator<ResultRow> {
        private var hasNext = false
            set(value) {
                field = value
                if (!field) {
                    val statement = rs.statement
                    rs.close()
                    statement?.close()
                    transaction.openResultSetsCount--
                }
            }

        private val fieldsIndex = set.realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

        init {
            hasNext = rs.next()
            if (hasNext) trackResultSet(transaction)
        }

        override operator fun next(): ResultRow {
            if (!hasNext) throw NoSuchElementException()
            val result = ResultRow.create(rs, fieldsIndex)
            hasNext = rs.next()
            return result
        }

        override fun hasNext(): Boolean = hasNext
    }

    companion object {
        private fun trackResultSet(transaction: Transaction) {
            val threshold = transaction.db.config.logTooMuchResultSetsThreshold
            if (threshold > 0 && threshold < transaction.openResultSetsCount) {
                val message =
                    "Current opened result sets size ${transaction.openResultSetsCount} exceeds $threshold threshold for transaction ${transaction.id} "
                val stackTrace = Exception(message).stackTraceToString()
                exposedLogger.error(stackTrace)
            }
            transaction.openResultSetsCount++
        }
    }
}

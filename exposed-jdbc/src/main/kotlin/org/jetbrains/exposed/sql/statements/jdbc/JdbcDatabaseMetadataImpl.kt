package org.jetbrains.exposed.sql.statements.jdbc

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import java.math.BigDecimal
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Class responsible for retrieving and storing information about the JDBC driver and underlying DBMS, using [metadata].
 */
class JdbcDatabaseMetadataImpl(database: String, val metadata: DatabaseMetaData) : ExposedDatabaseMetadata(database) {
    override val url: String by lazyMetadata { url }

    override val version: BigDecimal by lazyMetadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion") }

    override val majorVersion: Int by lazyMetadata { databaseMajorVersion }

    override val minorVersion: Int by lazyMetadata { databaseMinorVersion }

    override val databaseDialectName: String by lazyMetadata {
        when (driverName) {
            "MySQL-AB JDBC Driver", "MySQL Connector/J", "MySQL Connector Java" -> MysqlDialect.dialectName

            "MariaDB Connector/J" -> MariaDBDialect.dialectName
            "SQLite JDBC" -> SQLiteDialect.dialectName
            "H2 JDBC Driver" -> H2Dialect.dialectName
            "pgjdbc-ng" -> PostgreSQLNGDialect.dialectName
            "PostgreSQL JDBC - NG" -> PostgreSQLNGDialect.dialectName
            "PostgreSQL JDBC Driver" -> PostgreSQLDialect.dialectName
            "Oracle JDBC driver" -> OracleDialect.dialectName
            else -> {
                if (driverName.startsWith("Microsoft JDBC Driver ")) {
                    SQLServerDialect.dialectName
                } else {
                    Database.getDialectName(url) ?: error("Unsupported driver $driverName detected")
                }
            }
        }
    }

    private val databaseName
        get() = when (databaseDialectName) {
            MysqlDialect.dialectName, MariaDBDialect.dialectName -> currentSchema!!
            else -> database
        }

    override val databaseDialectMode: String? by lazy {
        val dialect = currentDialect
        if (dialect !is H2Dialect) null

        val (settingNameField, settingValueField) = when ((dialect as H2Dialect).majorVersion) {
            H2Dialect.H2MajorVersion.One -> "NAME" to "VALUE"
            H2Dialect.H2MajorVersion.Two -> "SETTING_NAME" to "SETTING_VALUE"
        }

        @Language("H2")
        val modeQuery = "SELECT $settingValueField FROM INFORMATION_SCHEMA.SETTINGS WHERE $settingNameField = 'MODE'"
        TransactionManager.current().exec(modeQuery) { rs ->
            rs.iterate { getString(settingValueField) }
        }?.firstOrNull()
    }

    override val databaseProductVersion by lazyMetadata { databaseProductVersion!! }

    override val defaultIsolationLevel: Int by lazyMetadata { defaultTransactionIsolation }

    override val supportsAlterTableWithAddColumn by lazyMetadata { supportsAlterTableWithAddColumn() }

    override val supportsAlterTableWithDropColumn by lazyMetadata { supportsAlterTableWithDropColumn() }

    override val supportsMultipleResultSets by lazyMetadata { supportsMultipleResultSets() }

    override val supportsSelectForUpdate: Boolean by lazyMetadata { supportsSelectForUpdate() }

    override fun supportsLimitWithUpdateOrDelete(): Boolean {
        return when (currentDialect) {
            is SQLiteDialect -> {
                try {
                    val transaction = TransactionManager.current()
                    transaction.exec("""SELECT sqlite_compileoption_used("ENABLE_UPDATE_DELETE_LIMIT");""") { rs ->
                        rs.next()
                        rs.getBoolean(1)
                    } == true
                } catch (_: SQLException) {
                    false
                }
            }
            is PostgreSQLDialect -> false
            else -> true
        }
    }

    override val identifierManager: IdentifierManagerApi by lazyMetadata {
        identityManagerCache.getOrPut(url) {
            JdbcIdentifierManager(this)
        }
    }

    private var currentSchema: String? = null
        get() {
            if (field == null) {
                field = try {
                    when (databaseDialectName) {
                        MysqlDialect.dialectName, MariaDBDialect.dialectName -> metadata.connection.catalog.orEmpty()
                        OracleDialect.dialectName -> metadata.connection.schema ?: databaseName
                        else -> metadata.connection.schema.orEmpty()
                    }
                } catch (_: Throwable) {
                    ""
                }
            }
            return field!!
        }

    override fun resetCurrentScheme() {
        currentSchema = null
    }

    private inner class CachableMapWithDefault<K, V>(
        private val map: MutableMap<K, V> = mutableMapOf(),
        val default: (K) -> V
    ) : Map<K, V> by map {
        override fun get(key: K): V? = map.getOrPut(key) { default(key) }
        override fun containsKey(key: K): Boolean = true
        override fun isEmpty(): Boolean = false

        override val entries: Set<Map.Entry<K, V>>
            get() = throw UnsupportedOperationException(
                "The entries field should not be used on CachableMapWithDefault because the lazy population of the collection for missing keys " +
                    "and entries may lead to inconsistencies between calls."
            )

        override val keys: Set<K>
            get() = throw UnsupportedOperationException(
                "The keys field should not be used on CachableMapWithDefault because the lazy population of the collection for missing keys " +
                    "and keys may lead to inconsistencies between calls."
            )
    }

    override val tableNames: Map<String, List<String>>
        get() = CachableMapWithDefault(default = { schemeName ->
            tableNamesFor(schemeName)
        })

    private fun tableNamesFor(scheme: String): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect
        val (catalogName, schemeName) = when {
            useCatalogInsteadOfScheme -> scheme to "%"
            currentDialect is OracleDialect -> databaseName to scheme.ifEmpty { databaseName }
            else -> databaseName to scheme.ifEmpty { "%" }
        }
        val resultSet = getTables(catalogName, schemeName, "%", arrayOf("TABLE"))
        return resultSet.iterate {
            val tableName = getString("TABLE_NAME")!!
            val fullTableName = when {
                useCatalogInsteadOfScheme -> getString("TABLE_CAT")?.let { "$it.$tableName" }
                else -> getString("TABLE_SCHEM")?.let { "$it.$tableName" }
            } ?: tableName
            identifierManager.inProperCase(fullTableName)
        }
    }

    override val schemaNames: List<String> get() = schemaNames()

    /** Returns a list of existing schema names. */
    private fun schemaNames(): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect

        val schemas = when {
            useCatalogInsteadOfScheme -> catalogs.iterate { getString("TABLE_CAT") }
            else -> schemas.iterate { getString("TABLE_SCHEM") }
        }

        return schemas.map { identifierManager.inProperCase(it) }
    }

    override fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata {
        val tablesInSchema = (tableNamesCache ?: tableNames).getValue(currentSchema!!)
        return SchemaMetadata(currentSchema!!, tablesInSchema)
    }

    private fun ResultSet.extractColumns(tableName: String): List<ColumnMetadata> {
        val prefetchedColumnTypes = currentDialect.fetchAllColumnTypes(tableName)
        val result = mutableListOf<ColumnMetadata>()
        while (next()) {
            result.add(asColumnMetadata(prefetchedColumnTypes))
        }
        return result
    }

    override fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val result = mutableMapOf<Table, List<ColumnMetadata>>()
        val useSchemaInsteadOfDatabase = currentDialect is MysqlDialect
        val tablesBySchema = tables.groupBy { identifierManager.inProperCase(it.schemaName ?: currentSchema!!) }

        for ((schema, schemaTables) in tablesBySchema.entries) {
            for (table in schemaTables) {
                val catalog = if (!useSchemaInsteadOfDatabase || schema == currentSchema!!) databaseName else schema
                val rs = metadata.getColumns(catalog, schema, table.nameInDatabaseCaseUnquoted(), "%")
                val columns = rs.extractColumns(tableName = table.nameInDatabaseCase())
                check(columns.isNotEmpty())
                result[table] = columns
                rs.close()
            }
        }

        return result
    }

    private fun ResultSet.asColumnMetadata(prefetchedColumnTypes: Map<String, String> = emptyMap()): ColumnMetadata {
        val defaultDbValue = getString("COLUMN_DEF")?.let { sanitizedDefault(it) }
        val autoIncrement = getString("IS_AUTOINCREMENT") == "YES"
        val type = getInt("DATA_TYPE")
        val name = getString("COLUMN_NAME")
        val nullable = getBoolean("NULLABLE")
        val size = getInt("COLUMN_SIZE").takeIf { it != 0 }
        val scale = getInt("DECIMAL_DIGITS").takeIf { it != 0 }
        val sqlType = currentDialect.getColumnType(this, prefetchedColumnTypes)

        return ColumnMetadata(name, type, sqlType, nullable, size, scale, autoIncrement, defaultDbValue?.takeIf { !autoIncrement })
    }

    /**
     * Here is the table of default values which are returned from the column `"COLUMN_DEF"` depending on how it was configured:
     *
     * - Not set: `varchar("any", 128).nullable()`
     * - Set null: `varchar("any", 128).nullable().default(null)`
     * - Set "NULL": `varchar("any", 128).nullable().default("NULL")`
     * ```
     * DB                  Not set    Set null                    Set "NULL"
     * SqlServer           null       "(NULL)"                    "('NULL')"
     * SQLite              null       "NULL"                      "'NULL'"
     * Postgres            null       "NULL::character varying"   "'NULL'::character varying"
     * PostgresNG          null       "NULL::character varying"   "'NULL'::character varying"
     * Oracle              null       "NULL "                     "'NULL' "
     * MySql5              null       null                        "NULL"
     * MySql8              null       null                        "NULL"
     * MariaDB3            "NULL"     "NULL"                      "'NULL'"
     * MariaDB2            "NULL"     "NULL"                      "'NULL'"
     * H2V1                null       "NULL"                      "'NULL'"
     * H2V1 (MySql)        null       "NULL"                      "'NULL'"
     * H2V2                null       "NULL"                      "'NULL'"
     * H2V2 (MySql)        null       "NULL"                      "'NULL'"
     * H2V2 (MariaDB)      null       "NULL"                      "'NULL'"
     * H2V2 (PSQL)         null       "NULL"                      "'NULL'"
     * H2V2 (Oracle)       null       "NULL"                      "'NULL'"
     * H2V2 (SqlServer)    null       "NULL"                      "'NULL'"
     * ```
     * According to this table there is no simple rule of what is the default value. It should be checked
     * for each DB (or groups of DBs) specifically.
     * In the case of MySql and MariaDB it's also not possible to say whether was default value skipped or
     * explicitly set to `null`.
     *
     * @return `null` - if the value was set to `null` or not configured. `defaultValue` in other case.
     */
    private fun sanitizedDefault(defaultValue: String): String? {
        val dialect = currentDialect
        val h2Mode = dialect.h2Mode
        return when {
            // Check for MariaDB must be before MySql because MariaDBDialect as a class inherits MysqlDialect
            dialect is MariaDBDialect || h2Mode == H2CompatibilityMode.MariaDB -> when {
                defaultValue.startsWith("b'") -> defaultValue.substringAfter("b'").trim('\'')
                else -> defaultValue.extractNullAndStringFromDefaultValue()
            }
            // It's the special case, because MySql returns default string "NULL" as string "NULL", but other DBs return it as "'NULL'"
            dialect is MysqlDialect && defaultValue == "NULL" -> defaultValue
            dialect is MysqlDialect || h2Mode == H2CompatibilityMode.MySQL -> when {
                defaultValue.startsWith("b'") -> defaultValue.substringAfter("b'").trim('\'')
                else -> defaultValue.extractNullAndStringFromDefaultValue()
            }
            dialect is SQLServerDialect -> defaultValue.trim('(', ')').extractNullAndStringFromDefaultValue()
            dialect is OracleDialect -> defaultValue.trim().extractNullAndStringFromDefaultValue()
            else -> defaultValue.extractNullAndStringFromDefaultValue()
        }
    }

    private fun String.extractNullAndStringFromDefaultValue() = when {
        this.startsWith("NULL") -> null
        this.startsWith('\'') && this.endsWith('\'') -> this.trim('\'')
        else -> this
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    @Suppress("CyclomaticComplexMethod")
    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for (table in tables) {
            val transaction = TransactionManager.current()
            val (catalog, tableSchema) = tableCatalogAndSchema(table)

            existingIndicesCache.getOrPut(table) {
                val pkNames = metadata.getPrimaryKeys(
                    catalog,
                    tableSchema,
                    table.nameInDatabaseCaseUnquoted()
                ).let { rs ->
                    val names = arrayListOf<String>()
                    while (rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    rs.close()
                    names
                }

                val storedIndexTable = if
                    (tableSchema == currentSchema!! && currentDialect is OracleDialect) {
                    table.nameInDatabaseCase()
                } else {
                    table.nameInDatabaseCaseUnquoted()
                }
                val rs = metadata.getIndexInfo(catalog, tableSchema, storedIndexTable, false, false)

                val tmpIndices = hashMapOf<Triple<String, Boolean, Op.TRUE?>, MutableList<String>>()

                while (rs.next()) {
                    rs.getString("INDEX_NAME")?.let { indexName ->
                        // if index is function-based, SQLite & MySQL return null column_name metadata
                        val columnNameMetadata = rs.getString("COLUMN_NAME") ?: when (currentDialect) {
                            is MysqlDialect, is SQLiteDialect -> "\"\""
                            else -> null
                        }
                        columnNameMetadata?.let { columnName ->
                            val column = transaction.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
                                columnName
                            )
                            val isUnique = !rs.getBoolean("NON_UNIQUE")
                            val isPartial = if (rs.getString("FILTER_CONDITION").isNullOrEmpty()) null else Op.TRUE
                            tmpIndices.getOrPut(Triple(indexName, isUnique, isPartial)) { arrayListOf() }.add(column)
                        }
                    }
                }
                rs.close()
                val tColumns = table.columns.associateBy { transaction.identity(it) }
                tmpIndices.filterNot { it.key.first in pkNames }.mapNotNull { (index, columns) ->
                    val (functionBased, columnBased) = columns.distinct().partition { cn -> tColumns[cn] == null }
                    columnBased.map { cn -> tColumns[cn]!! }.takeIf { c -> c.size + functionBased.size == columns.size }?.let { c ->
                        Index(
                            c,
                            index.second,
                            index.first,
                            filterCondition = index.third,
                            functions = functionBased.map { stringLiteral(it) }.ifEmpty { null },
                            functionsTable = if (functionBased.isNotEmpty()) table else null
                        )
                    }
                }
            }
        }
        return HashMap(existingIndicesCache)
    }

    override fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        return tables.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(table)
            metadata.getPrimaryKeys(catalog, tableSchema, table.nameInDatabaseCaseUnquoted()).let { rs ->
                val columnNames = mutableListOf<String>()
                var pkName = ""
                while (rs.next()) {
                    rs.getString("PK_NAME")?.let { pkName = it }
                    columnNames += rs.getString("COLUMN_NAME")
                }
                rs.close()
                if (pkName.isEmpty()) null else PrimaryKeyMetadata(pkName, columnNames)
            }
        }
    }

    override fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> {
        if (currentDialect !is PostgreSQLDialect) return emptyMap()

        val transaction = TransactionManager.current()
        return tables.associateWith { table ->
            val (_, tableSchema) = tableCatalogAndSchema(table)
            transaction.exec(
                """
                    SELECT seq_details.sequence_name,
                    seq_details.start,
                    seq_details.increment,
                    seq_details.max,
                    seq_details.min,
                    seq_details.cache,
                    seq_details.cycle
                    FROM pg_catalog.pg_namespace tns
                             INNER JOIN pg_catalog.pg_class t ON tns.oid = t.relnamespace AND t.relkind IN ('p', 'r')
                             INNER JOIN pg_catalog.pg_depend d ON t.oid = d.refobjid
                             LEFT OUTER JOIN (
                                SELECT s.relname AS sequence_name,
                                seq.seqstart AS start,
                                seq.seqincrement AS increment,
                                seq.seqmax AS max,
                                seq.seqmin AS min,
                                seq.seqcache AS cache,
                                seq.seqcycle AS cycle,
                                s.oid AS seq_id
                                FROM pg_catalog.pg_sequence seq
                                JOIN pg_catalog.pg_class s ON s.oid = seq.seqrelid AND s.relkind = 'S'
                                JOIN pg_catalog.pg_namespace sns ON s.relnamespace = sns.oid
                                WHERE sns.nspname = '$tableSchema'
                             ) seq_details ON seq_details.seq_id = d.objid
                    WHERE tns.nspname = '$tableSchema' AND t.relname = '${table.nameInDatabaseCaseUnquoted()}'
                """.trimIndent()
            ) { rs ->
                val tmpSequences = mutableListOf<Sequence>()
                while (rs.next()) {
                    rs.getString("sequence_name")?.let {
                        tmpSequences.add(
                            Sequence(
                                it,
                                rs.getLong("start"),
                                rs.getLong("increment"),
                                rs.getLong("min"),
                                rs.getLong("max"),
                                rs.getBoolean("cycle"),
                                rs.getLong("cache")
                            )
                        )
                    }
                }
                rs.close()
                tmpSequences
            }.orEmpty()
        }
    }

    @Suppress("MagicNumber")
    override fun sequences(): List<String> {
        val dialect = currentDialect
        val transaction = TransactionManager.current()
        val fieldName = "SEQUENCE_NAME"
        return when (dialect) {
            is OracleDialect -> transaction.exec("SELECT $fieldName FROM USER_SEQUENCES") { rs ->
                rs.iterate {
                    val seqName = getString(fieldName)
                    if (identifierManager.isDotPrefixedAndUnquoted(seqName)) "\"$seqName\"" else seqName
                }
            }
            is H2Dialect -> transaction.exec("SELECT $fieldName FROM INFORMATION_SCHEMA.SEQUENCES") { rs ->
                rs.iterate {
                    val seqName = getString(fieldName)
                    if (dialect.h2Mode == H2CompatibilityMode.Oracle && identifierManager.isDotPrefixedAndUnquoted(seqName)) {
                        "\"$seqName\""
                    } else {
                        seqName
                    }
                }
            }
            is SQLServerDialect -> transaction.exec("SELECT name AS $fieldName FROM sys.sequences") { rs ->
                rs.iterate {
                    getString(fieldName)
                }
            }
            else -> metadata.getTables(null, null, null, arrayOf("SEQUENCE")).iterate {
                getString(3)
            }
        } ?: emptyList()
    }

    @Synchronized
    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCaseUnquoted() }

        val dialect = currentDialect

        return if (dialect is MysqlDialect) {
            val transaction = TransactionManager.current()
            val inTableList = allTables.keys.joinToString("','", prefix = " ku.TABLE_NAME IN ('", postfix = "')")
            val tableSchema = "'${tables.mapNotNull { it.schemaName }.toSet().singleOrNull() ?: currentSchema}'"
            val constraintsToLoad = HashMap<String, MutableMap<String, ForeignKeyConstraint>>()
            transaction.exec(
                """
                    SELECT
                      rc.CONSTRAINT_NAME AS FK_NAME,
                      ku.TABLE_NAME AS FKTABLE_NAME,
                      ku.COLUMN_NAME AS FKCOLUMN_NAME,
                      ku.REFERENCED_TABLE_NAME AS PKTABLE_NAME,
                      ku.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME,
                      rc.UPDATE_RULE,
                      rc.DELETE_RULE
                    FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                      INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku
                        ON ku.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME
                    WHERE ku.TABLE_SCHEMA = $tableSchema
                      AND ku.CONSTRAINT_SCHEMA = $tableSchema
                      AND rc.CONSTRAINT_SCHEMA = $tableSchema
                      AND $inTableList
                    ORDER BY ku.ORDINAL_POSITION
                """.trimIndent()
            ) { rs ->
                while (rs.next()) {
                    rs.extractForeignKeys(allTables, true)?.let { (fromTableName, fk) ->
                        constraintsToLoad.getOrPut(fromTableName) { mutableMapOf() }
                            .merge(fk.fkName, fk, ForeignKeyConstraint::plus)
                    }
                }
            }
            // This ensures MySQL/MariaDB have same behavior as before: a map entry for every table even if no FKs
            allTables.keys.forEach { constraintsToLoad.putIfAbsent(it, mutableMapOf()) }
            constraintsToLoad.mapValues { (_, v) -> v.values.toList() }
        } else {
            allTables.keys.associateWith { table ->
                val (catalog, tableSchema) = tableCatalogAndSchema(allTables[table]!!)
                metadata.getImportedKeys(catalog, identifierManager.inProperCase(tableSchema), table)
                    .iterate { extractForeignKeys(allTables, false) }
                    .filterNotNull()
                    .unzip().second
                    .groupBy { it.fkName }.values
                    .map { it.reduce(ForeignKeyConstraint::plus) }
            }
        }
    }

    private fun ResultSet.extractForeignKeys(
        allTables: Map<String, Table>,
        isMysqlDialect: Boolean
    ): Pair<String, ForeignKeyConstraint>? {
        val fromTableName = getString("FKTABLE_NAME")!!
        if (isMysqlDialect && fromTableName !in allTables.keys) return null
        val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
            getString("FKCOLUMN_NAME")!!
        )
        val fromColumn = allTables[fromTableName]?.columns?.firstOrNull {
            val identifier = if (isMysqlDialect) it.nameInDatabaseCase() else it.name
            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(identifier) == fromColumnName
        } ?: return null // Do not crash if there are missing fields in Exposed's tables
        val constraintName = getString("FK_NAME")!!
        val targetTableName = getString("PKTABLE_NAME")!!
        val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
            if (isMysqlDialect) {
                getString("PKCOLUMN_NAME")!!
            } else {
                identifierManager.inProperCase(getString("PKCOLUMN_NAME")!!)
            }
        )
        val targetColumn = allTables[targetTableName]?.columns?.firstOrNull {
            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
        } ?: return null // Do not crash if there are missing fields in Exposed's tables
        val constraintUpdateRule = getObject("UPDATE_RULE")?.toString()?.let { resolveReferenceOption(it) }
        val constraintDeleteRule = getObject("DELETE_RULE")?.toString()?.let { resolveReferenceOption(it) }
        return fromTableName to ForeignKeyConstraint(
            target = targetColumn,
            from = fromColumn,
            onUpdate = constraintUpdateRule,
            onDelete = constraintDeleteRule,
            name = constraintName
        )
    }

    @OptIn(InternalApi::class)
    override fun resolveReferenceOption(refOption: String): ReferenceOption? {
        val dialect = currentDialect

        // MySQL/MariaDB use custom query that returns string-name values
        if (dialect is MysqlDialect) {
            return ReferenceOption.valueOf(refOption.replace(" ", "_"))
        }

        val refOptionInt = refOption.toIntOrNull() ?: return null

        return when (refOptionInt) {
            DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
            DatabaseMetaData.importedKeyRestrict -> {
                val restrictNotSupported = dialect is OracleDialect ||
                    dialect.h2Mode == H2CompatibilityMode.Oracle ||
                    dialect.h2Mode == H2CompatibilityMode.SQLServer
                if (restrictNotSupported) ReferenceOption.NO_ACTION else ReferenceOption.RESTRICT
            }
            DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
            DatabaseMetaData.importedKeyNoAction -> ReferenceOption.NO_ACTION
            DatabaseMetaData.importedKeySetDefault -> ReferenceOption.SET_DEFAULT
            else -> currentDialect.defaultReferenceOption
        }
    }

    /**
     * Returns the name of the database in which a [table] is found, as well as it's schema name.
     *
     * If the table name does not include a schema prefix, the metadata value `currentScheme` is used instead.
     *
     * MySQL/MariaDB are special cases in that a schema definition is treated like a separate database. This means that
     * a connection to 'testDb' with a table defined as 'my_schema.my_table' will only successfully find the table's
     * metadata if 'my_schema' is used as the database name.
     */
    private fun tableCatalogAndSchema(table: Table): Pair<String, String> {
        val tableSchema = identifierManager.inProperCase(table.schemaName ?: currentSchema!!)
        return if (currentDialect is MysqlDialect && tableSchema != currentSchema!!) {
            tableSchema to tableSchema
        } else {
            databaseName to tableSchema
        }
    }

    @Synchronized
    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    private fun <T> lazyMetadata(body: DatabaseMetaData.() -> T) = lazy { metadata.body() }

    companion object {
        private val identityManagerCache = ConcurrentHashMap<String, JdbcIdentifierManager>()
    }
}

private fun <T> ResultSet.iterate(body: ResultSet.() -> T): List<T> {
    val result = arrayListOf<T>()
    while (next()) {
        result.add(body())
    }
    close()
    return result
}

package de.dennisguse.opentracks.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Car costs other than fuel; kept in their own database so existing databases keep their versions. */
enum class ExpenseCategory(val label: String, val emoji: String) {
    MANUTENCAO("Manutenção / revisão", "🔧"),
    PECAS("Peças e reparos", "⚙️"),
    PNEUS("Pneus", "🛞"),
    PEDAGIO("Pedágio", "🛣️"),
    ESTACIONAMENTO("Estacionamento", "🅿️"),
    LAVAGEM("Lavagem", "🧽"),
    SEGURO("Seguro", "🛡️"),
    IPVA("IPVA / licenciamento", "📄"),
    MULTA("Multa", "🚨"),
    OUTROS("Outros", "📦");

    companion object {
        fun of(name: String?) = values().firstOrNull { it.name == name } ?: OUTROS
    }
}

data class Expense(
    val id: Long,
    val dateTime: Long,
    val category: ExpenseCategory,
    val description: String,
    val amount: Double,
    val odometerKm: Double?,
    /** Maintenance reminder marked as done by this expense, if any. */
    val maintenanceId: Int?
)

class ExpenseDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, "cartracks_expenses.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE expenses (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_time INTEGER NOT NULL,
                category TEXT NOT NULL,
                description TEXT,
                amount REAL NOT NULL,
                odometer_km REAL,
                maintenance_id INTEGER
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<Expense> = readableDatabase.query("expenses", null, null, null, null, null, "date_time DESC").use { c ->
        val list = mutableListOf<Expense>()
        while (c.moveToNext()) {
            list += Expense(
                id = c.getLong(c.getColumnIndexOrThrow("_id")),
                dateTime = c.getLong(c.getColumnIndexOrThrow("date_time")),
                category = ExpenseCategory.of(c.getString(c.getColumnIndexOrThrow("category"))),
                description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
                amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
                odometerKm = c.getColumnIndexOrThrow("odometer_km").let { if (c.isNull(it)) null else c.getDouble(it) },
                maintenanceId = c.getColumnIndexOrThrow("maintenance_id").let { if (c.isNull(it)) null else c.getInt(it) }
            )
        }
        list
    }

    private fun values(e: Expense) = ContentValues().apply {
        put("date_time", e.dateTime)
        put("category", e.category.name)
        put("description", e.description)
        put("amount", e.amount)
        if (e.odometerKm != null) put("odometer_km", e.odometerKm) else putNull("odometer_km")
        if (e.maintenanceId != null) put("maintenance_id", e.maintenanceId) else putNull("maintenance_id")
    }

    fun insert(e: Expense): Long = writableDatabase.insert("expenses", null, values(e))

    fun update(e: Expense) {
        writableDatabase.update("expenses", values(e), "_id = ?", arrayOf(e.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("expenses", "_id = ?", arrayOf(id.toString()))
    }

    /** Backup import: skip an expense already present (same moment, category and amount). */
    fun exists(e: Expense): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM expenses WHERE date_time = ? AND category = ? AND ABS(amount - ?) < 0.005 LIMIT 1",
        arrayOf(e.dateTime.toString(), e.category.name, e.amount.toString())
    ).use { it.moveToFirst() }

    companion object {
        @Volatile private var instance: ExpenseDatabaseHelper? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: ExpenseDatabaseHelper(context.applicationContext).also { instance = it }
        }
    }
}

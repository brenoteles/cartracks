package de.dennisguse.opentracks.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class FuelDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cartracks_fuel.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NAME = "fuel_records";
    public static final String COL_ID = "_id";
    public static final String COL_DATE_TIME = "date_time";
    public static final String COL_STATION_NAME = "station_name";
    public static final String COL_LITERS = "liters";
    public static final String COL_PRICE_PER_LITER = "price_per_liter";
    public static final String COL_TOTAL_COST = "total_cost";
    public static final String COL_ODOMETER = "odometer_km";
    public static final String COL_NOTES = "notes";
    public static final String COL_IS_FULL_TANK = "is_full_tank";
    public static final String COL_FUEL_TYPE = "fuel_type";

    private static FuelDatabaseHelper instance;

    public static synchronized FuelDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new FuelDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private FuelDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_DATE_TIME + " INTEGER, "
                + COL_STATION_NAME + " TEXT, "
                + COL_LITERS + " REAL, "
                + COL_PRICE_PER_LITER + " REAL, "
                + COL_TOTAL_COST + " REAL, "
                + COL_ODOMETER + " REAL, "
                + COL_NOTES + " TEXT, "
                + COL_IS_FULL_TANK + " INTEGER DEFAULT 1, "
                + COL_FUEL_TYPE + " TEXT DEFAULT 'Gasolina Comum');");

        seedMockFuelData(db);
    }

    private void seedMockFuelData(SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        long day = 86400000L;
        // 1. Tanque cheio inicial há 18 dias
        insertInitial(db, now - 18 * day, "Posto Shell Jardins", 48.0, 5.89, 282.72, 44100, "Tanque cheio", 1, "Gasolina Aditivada");
        // 2. Abastecimento parcial no meio do caminho há 12 dias
        insertInitial(db, now - 12 * day, "Posto Ipiranga Rebouças", 22.5, 3.89, 87.52, 44420, "Abastecimento rápido 20L", 0, "Etanol");
        // 3. Tanque cheio há 6 dias (permite cálculo real acumulando o abastecimento parcial!)
        insertInitial(db, now - 6 * day, "Posto Shell Jardins", 34.0, 5.85, 198.90, 44980, "Tanque cheio até a trava", 1, "Gasolina Comum");
        // 4. Abastecimento parcial recente há 2 dias
        insertInitial(db, now - 2 * day, "Posto BR Petrobras", 18.0, 5.79, 104.22, 45220, "Completado meio tanque", 0, "Gasolina Comum");
    }

    private void insertInitial(SQLiteDatabase db, long time, String station, double liters, double price, double total, double odo, String notes, int isFull, String fuelType) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE_TIME, time);
        cv.put(COL_STATION_NAME, station);
        cv.put(COL_LITERS, liters);
        cv.put(COL_PRICE_PER_LITER, price);
        cv.put(COL_TOTAL_COST, total);
        cv.put(COL_ODOMETER, odo);
        cv.put(COL_NOTES, notes);
        cv.put(COL_IS_FULL_TANK, isFull);
        cv.put(COL_FUEL_TYPE, fuelType);
        db.insert(TABLE_NAME, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_IS_FULL_TANK + " INTEGER DEFAULT 1;");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_FUEL_TYPE + " TEXT DEFAULT 'Gasolina Comum';");
            } catch (Exception ignored) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                onCreate(db);
            }
        }
    }

    public static class FuelEntry {
        public long id;
        public long dateTime;
        public String stationName;
        public double liters;
        public double pricePerLiter;
        public double totalCost;
        public double odometerKm;
        public String notes;
        public boolean isFullTank = true;
        public String fuelType = "Gasolina Comum";
    }

    public List<FuelEntry> getAllEntries() {
        List<FuelEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_NAME, null, null, null, null, null, COL_DATE_TIME + " DESC")) {
            while (c != null && c.moveToNext()) {
                FuelEntry e = new FuelEntry();
                e.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                e.dateTime = c.getLong(c.getColumnIndexOrThrow(COL_DATE_TIME));
                e.stationName = c.getString(c.getColumnIndexOrThrow(COL_STATION_NAME));
                e.liters = c.getDouble(c.getColumnIndexOrThrow(COL_LITERS));
                e.pricePerLiter = c.getDouble(c.getColumnIndexOrThrow(COL_PRICE_PER_LITER));
                e.totalCost = c.getDouble(c.getColumnIndexOrThrow(COL_TOTAL_COST));
                e.odometerKm = c.getDouble(c.getColumnIndexOrThrow(COL_ODOMETER));
                e.notes = c.getString(c.getColumnIndexOrThrow(COL_NOTES));
                int fullCol = c.getColumnIndex(COL_IS_FULL_TANK);
                if (fullCol >= 0) {
                    e.isFullTank = c.getInt(fullCol) == 1;
                }
                int typeCol = c.getColumnIndex(COL_FUEL_TYPE);
                if (typeCol >= 0) {
                    e.fuelType = c.getString(typeCol);
                    if (e.fuelType == null || e.fuelType.isEmpty()) {
                        e.fuelType = "Gasolina Comum";
                    }
                }
                list.add(e);
            }
        }
        return list;
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes) {
        addEntry(station, liters, price, total, odo, notes, true, "Gasolina Comum");
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes, boolean isFullTank, String fuelType) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE_TIME, System.currentTimeMillis());
        cv.put(COL_STATION_NAME, station);
        cv.put(COL_LITERS, liters);
        cv.put(COL_PRICE_PER_LITER, price);
        cv.put(COL_TOTAL_COST, total > 0 ? total : liters * price);
        cv.put(COL_ODOMETER, odo);
        cv.put(COL_NOTES, notes);
        cv.put(COL_IS_FULL_TANK, isFullTank ? 1 : 0);
        cv.put(COL_FUEL_TYPE, fuelType != null ? fuelType : "Gasolina Comum");
        db.insert(TABLE_NAME, null, cv);
    }
}

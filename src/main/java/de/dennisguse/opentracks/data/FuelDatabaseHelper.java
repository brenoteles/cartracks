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
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "fuel_records";
    public static final String COL_ID = "_id";
    public static final String COL_DATE_TIME = "date_time";
    public static final String COL_STATION_NAME = "station_name";
    public static final String COL_LITERS = "liters";
    public static final String COL_PRICE_PER_LITER = "price_per_liter";
    public static final String COL_TOTAL_COST = "total_cost";
    public static final String COL_ODOMETER = "odometer_km";
    public static final String COL_NOTES = "notes";

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
                + COL_NOTES + " TEXT);");

        // Seed mock initial fuel records
        long now = System.currentTimeMillis();
        long day = 86400000L;
        insertInitial(db, now - 3 * day, "Posto Shell Jardins", 42.0, 5.89, 247.38, 45200, "Tanque cheio Gasolina V-Power");
        insertInitial(db, now - 8 * day, "Posto Ipiranga Rebouças", 38.5, 5.79, 222.91, 44750, "Gasolina Comum");
        insertInitial(db, now - 14 * day, "Posto Shell Jardins", 40.0, 5.85, 234.00, 44290, "Tanque cheio");
    }

    private void insertInitial(SQLiteDatabase db, long time, String station, double liters, double price, double total, double odo, String notes) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE_TIME, time);
        cv.put(COL_STATION_NAME, station);
        cv.put(COL_LITERS, liters);
        cv.put(COL_PRICE_PER_LITER, price);
        cv.put(COL_TOTAL_COST, total);
        cv.put(COL_ODOMETER, odo);
        cv.put(COL_NOTES, notes);
        db.insert(TABLE_NAME, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
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
                list.add(e);
            }
        }
        return list;
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE_TIME, System.currentTimeMillis());
        cv.put(COL_STATION_NAME, station);
        cv.put(COL_LITERS, liters);
        cv.put(COL_PRICE_PER_LITER, price);
        cv.put(COL_TOTAL_COST, total > 0 ? total : liters * price);
        cv.put(COL_ODOMETER, odo);
        cv.put(COL_NOTES, notes);
        db.insert(TABLE_NAME, null, cv);
    }
}
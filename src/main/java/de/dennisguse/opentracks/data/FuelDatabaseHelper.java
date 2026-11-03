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
    private static final int DATABASE_VERSION = 3;

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
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";

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
                + COL_FUEL_TYPE + " TEXT DEFAULT 'Gasolina Comum', "
                + COL_LATITUDE + " REAL, "
                + COL_LONGITUDE + " REAL);");
        // Fresh database starts clean with 0 mock entries for real user data
    }

    private void seedMockFuelData(SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        long day = 86400000L;
        // 1. Tanque cheio inicial há 18 dias (Com localização definida no mapa)
        insertInitial(db, now - 18 * day, "Posto Shell Jardins", 48.0, 5.89, 282.72, 44100, "Tanque cheio", 1, "Gasolina Aditivada", -23.5672, -46.6685);
        // 2. Abastecimento parcial no meio do caminho há 12 dias (Com localização definida no mapa)
        insertInitial(db, now - 12 * day, "Posto Ipiranga Rebouças", 22.5, 3.89, 87.52, 44420, "Abastecimento rápido 20L", 0, "Etanol", -23.5618, -46.6720);
        // 3. Tanque cheio há 6 dias (Com localização definida no mapa)
        insertInitial(db, now - 6 * day, "Posto Shell Jardins", 34.0, 5.85, 198.90, 44980, "Tanque cheio até a trava", 1, "Gasolina Comum", -23.5672, -46.6685);
        // 4. Abastecimento parcial recente há 2 dias (Sem localização ainda — permite testar adicionar localização depois!)
        insertInitial(db, now - 2 * day, "Posto BR Petrobras", 18.0, 5.79, 104.22, 45220, "Completado meio tanque", 0, "Gasolina Comum", null, null);
    }

    private void insertInitial(SQLiteDatabase db, long time, String station, double liters, double price, double total, double odo, String notes, int isFull, String fuelType, Double lat, Double lon) {
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
        if (lat != null && lon != null) {
            cv.put(COL_LATITUDE, lat);
            cv.put(COL_LONGITUDE, lon);
        } else {
            cv.putNull(COL_LATITUDE);
            cv.putNull(COL_LONGITUDE);
        }
        db.insert(TABLE_NAME, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_IS_FULL_TANK + " INTEGER DEFAULT 1;");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_FUEL_TYPE + " TEXT DEFAULT 'Gasolina Comum';");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LATITUDE + " REAL;");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LONGITUDE + " REAL;");
                // Populate mock locations for existing seeded stations
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_LATITUDE + " = -23.5672, " + COL_LONGITUDE + " = -46.6685 WHERE " + COL_STATION_NAME + " LIKE '%Shell%';");
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_LATITUDE + " = -23.5618, " + COL_LONGITUDE + " = -46.6720 WHERE " + COL_STATION_NAME + " LIKE '%Ipiranga%';");
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
        public Double latitude = null;
        public Double longitude = null;
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
                int latCol = c.getColumnIndex(COL_LATITUDE);
                int lonCol = c.getColumnIndex(COL_LONGITUDE);
                if (latCol >= 0 && lonCol >= 0 && !c.isNull(latCol) && !c.isNull(lonCol)) {
                    e.latitude = c.getDouble(latCol);
                    e.longitude = c.getDouble(lonCol);
                }
                list.add(e);
            }
        }
        return list;
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes) {
        addEntry(station, liters, price, total, odo, notes, true, "Gasolina Comum", null, null);
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes, boolean isFullTank, String fuelType) {
        addEntry(station, liters, price, total, odo, notes, isFullTank, fuelType, null, null);
    }

    public void addEntry(String station, double liters, double price, double total, double odo, String notes, boolean isFullTank, String fuelType, Double latitude, Double longitude) {
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
        if (latitude != null && longitude != null) {
            cv.put(COL_LATITUDE, latitude);
            cv.put(COL_LONGITUDE, longitude);
        } else {
            cv.putNull(COL_LATITUDE);
            cv.putNull(COL_LONGITUDE);
        }
        db.insert(TABLE_NAME, null, cv);
    }

    public void updateEntryLocation(long id, double latitude, double longitude) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LATITUDE, latitude);
        cv.put(COL_LONGITUDE, longitude);
        db.update(TABLE_NAME, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void updateStationName(long id, String newStationName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STATION_NAME, newStationName);
        db.update(TABLE_NAME, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void updateEntry(long id, String station, double liters, double price, double total, double odo, String notes, boolean isFull, String fuelType) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STATION_NAME, station);
        cv.put(COL_LITERS, liters);
        cv.put(COL_PRICE_PER_LITER, price);
        cv.put(COL_TOTAL_COST, total > 0 ? total : liters * price);
        cv.put(COL_ODOMETER, odo);
        cv.put(COL_NOTES, notes);
        cv.put(COL_IS_FULL_TANK, isFull ? 1 : 0);
        cv.put(COL_FUEL_TYPE, fuelType);
        db.update(TABLE_NAME, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void deleteEntry(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void deleteAllEntries() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    public void deleteMockEntries() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, COL_STATION_NAME + " LIKE '%Shell Jardins%' OR " +
                COL_STATION_NAME + " LIKE '%Ipiranga Rebouças%' OR " +
                COL_STATION_NAME + " LIKE '%Ipiranga Faria Lima%' OR " +
                COL_STATION_NAME + " LIKE '%BR Petrobras Pinheiros%'", null);
    }

    public double getLatestPricePerLiter() {
        List<FuelEntry> list = getAllEntries();
        if (!list.isEmpty() && list.get(0).pricePerLiter > 0) {
            return list.get(0).pricePerLiter;
        }
        return 5.89;
    }

    /**
     * Computes the real average fuel efficiency (km/L) using full-to-full tank odometer deltas.
     */
    public double getOverallRealKmL() {
        List<FuelEntry> list = getAllEntries();
        if (list.size() < 2) return 12.8;

        // Sort ascending by odometer
        list.sort((a, b) -> Double.compare(a.odometerKm, b.odometerKm));

        int firstFullIdx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isFullTank && list.get(i).odometerKm > 0) {
                firstFullIdx = i;
                break;
            }
        }
        if (firstFullIdx == -1) return 12.8;

        int lastFullIdx = -1;
        for (int i = list.size() - 1; i > firstFullIdx; i--) {
            if (list.get(i).isFullTank && list.get(i).odometerKm > 0) {
                lastFullIdx = i;
                break;
            }
        }
        if (lastFullIdx == -1 || lastFullIdx <= firstFullIdx) return 12.8;

        double deltaKm = list.get(lastFullIdx).odometerKm - list.get(firstFullIdx).odometerKm;
        double sumLiters = 0.0;
        for (int i = firstFullIdx + 1; i <= lastFullIdx; i++) {
            sumLiters += list.get(i).liters;
        }

        if (deltaKm > 0 && sumLiters > 0) {
            double kmL = deltaKm / sumLiters;
            if (kmL >= 4.0 && kmL <= 30.0) {
                return kmL;
            }
        }
        return 12.8;
    }
}

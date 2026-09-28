package com.saleh.enezi.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GroceryDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "alazzi_grocery_runtime_v5.db";
    public static final int DATABASE_VERSION = 3;

    public static final String TABLE_SUPPLIERS = "suppliers";
    public static final String TABLE_CUSTOMERS = "customers";
    public static final String TABLE_PRODUCTS = "products";
    public static final String TABLE_INVOICES = "invoices";
    public static final String TABLE_INVOICE_ITEMS = "invoice_items";
    public static final String TABLE_PURCHASES = "purchases";
    public static final String TABLE_PURCHASE_ITEMS = "purchase_items";
    public static final String TABLE_TRANSACTIONS = "transactions";
    public static final String TABLE_STOCK = "stock";

    public GroceryDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SUPPLIERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "phone TEXT, " +
                "details TEXT, " +
                "balance REAL NOT NULL DEFAULT 0, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CUSTOMERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "phone TEXT, " +
                "details TEXT, " +
                "balance REAL NOT NULL DEFAULT 0, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PRODUCTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "category TEXT, " +
                "unit TEXT, " +
                "buy_price REAL NOT NULL DEFAULT 0, " +
                "sell_price REAL NOT NULL DEFAULT 0, " +
                "qty REAL NOT NULL DEFAULT 0, " +
                "barcode TEXT, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_INVOICES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "no TEXT NOT NULL, " +
                "customer_id INTEGER, " +
                "customer_name TEXT, " +
                "total REAL NOT NULL DEFAULT 0, " +
                "paid REAL NOT NULL DEFAULT 0, " +
                "date TEXT, " +
                "sale_type TEXT, " +
                "details TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_INVOICE_ITEMS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "invoice_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "qty REAL NOT NULL DEFAULT 0, " +
                "unit TEXT, " +
                "price REAL NOT NULL DEFAULT 0, " +
                "total REAL NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "no TEXT NOT NULL, " +
                "supplier_id INTEGER, " +
                "supplier_name TEXT, " +
                "total REAL NOT NULL DEFAULT 0, " +
                "paid REAL NOT NULL DEFAULT 0, " +
                "date TEXT, " +
                "details TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASE_ITEMS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "purchase_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "qty REAL NOT NULL DEFAULT 0, " +
                "unit TEXT, " +
                "price REAL NOT NULL DEFAULT 0, " +
                "total REAL NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER, " +
                "supplier_id INTEGER, " +
                "type INTEGER NOT NULL, " +
                "amount REAL NOT NULL DEFAULT 0, " +
                "details TEXT, " +
                "date TEXT, " +
                "invoice_no TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STOCK + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "qty REAL NOT NULL DEFAULT 0, " +
                "unit TEXT, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_SUPPLIERS + " ADD COLUMN balance REAL NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "customer_id INTEGER, " +
                    "supplier_id INTEGER, " +
                    "type INTEGER NOT NULL, " +
                    "amount REAL NOT NULL DEFAULT 0, " +
                    "details TEXT, " +
                    "date TEXT, " +
                    "invoice_no TEXT)");
        }
    }

    public long addSupplier(String name, String phone, String details) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name.trim());
        values.put("phone", phone == null ? "" : phone.trim());
        values.put("details", details == null ? "" : details.trim());
        values.put("balance", 0.0);
        return db.insert(TABLE_SUPPLIERS, null, values);
    }

    public long addCustomer(String name, String phone, String details) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name.trim());
        values.put("phone", phone == null ? "" : phone.trim());
        values.put("details", details == null ? "" : details.trim());
        values.put("balance", 0.0);
        return db.insert(TABLE_CUSTOMERS, null, values);
    }

    public long addProduct(String name, String category, String unit, double buyPrice, double sellPrice, double qty, String barcode) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name.trim());
        values.put("category", category == null ? "" : category.trim());
        values.put("unit", unit == null ? "" : unit.trim());
        values.put("buy_price", buyPrice);
        values.put("sell_price", sellPrice);
        values.put("qty", qty);
        values.put("barcode", barcode == null ? "" : barcode.trim());
        return db.insert(TABLE_PRODUCTS, null, values);
    }

    public Cursor getSuppliers() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_SUPPLIERS, null, null, null, null, null, "name ASC");
    }

    public Cursor getCustomers() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_CUSTOMERS, null, null, null, null, null, "name ASC");
    }

    public Cursor getProducts() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_PRODUCTS, null, null, null, null, null, "name ASC");
    }

    public boolean updateSupplierBalance(long supplierId, double delta) {
        if (supplierId <= 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_SUPPLIERS + " SET balance = balance + ? WHERE id = ?", new Object[]{delta, supplierId});
        return true;
    }

    public boolean updateCustomerBalance(long customerId, double delta) {
        if (customerId <= 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_CUSTOMERS + " SET balance = balance + ? WHERE id = ?", new Object[]{delta, customerId});
        return true;
    }

    public void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }
}

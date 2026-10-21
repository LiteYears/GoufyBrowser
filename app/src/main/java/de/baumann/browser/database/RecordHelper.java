package de.baumann.browser.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import de.baumann.browser.unit.RecordUnit;

class RecordHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "browser.db";
    private static final int DATABASE_VERSION = 2;

    RecordHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(RecordUnit.CREATE_WHITELIST);
        database.execSQL(RecordUnit.CREATE_JAVASCRIPT);
        database.execSQL(RecordUnit.CREATE_DOM);
        database.execSQL(RecordUnit.CREATE_COOKIE);
        database.execSQL(RecordUnit.CREATE_BOOKMARK);
        database.execSQL(RecordUnit.CREATE_HISTORY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        database.execSQL(RecordUnit.CREATE_HISTORY);
    }
}

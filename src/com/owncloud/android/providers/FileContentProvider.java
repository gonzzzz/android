/* ownCloud Android client application
 *   Copyright (C) 2011  Bartek Przybylski
 *   Copyright (C) 2012-2013 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.providers;

import java.util.ArrayList;
import java.util.HashMap;

import com.owncloud.android.R;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.db.ProviderMeta;
import com.owncloud.android.db.ProviderMeta.ProviderTableMeta;
import com.owncloud.android.utils.Log_OC;



import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

/**
 * The ContentProvider for the ownCloud App.
 * 
 * @author Bartek Przybylski
 * @author David A. Velasco
 * 
 */
public class FileContentProvider extends ContentProvider {

    private DataBaseHelper mDbHelper;

    private static HashMap<String, String> mProjectionMap;
    static {
        mProjectionMap = new HashMap<String, String>();
        mProjectionMap.put(ProviderTableMeta._ID, ProviderTableMeta._ID);
        mProjectionMap.put(ProviderTableMeta.FILE_PARENT,
                ProviderTableMeta.FILE_PARENT);
        mProjectionMap.put(ProviderTableMeta.FILE_PATH,
                ProviderTableMeta.FILE_PATH);
        mProjectionMap.put(ProviderTableMeta.FILE_NAME,
                ProviderTableMeta.FILE_NAME);
        mProjectionMap.put(ProviderTableMeta.FILE_CREATION,
                ProviderTableMeta.FILE_CREATION);
        mProjectionMap.put(ProviderTableMeta.FILE_MODIFIED,
                ProviderTableMeta.FILE_MODIFIED);
        mProjectionMap.put(ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA,
                ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA);
        mProjectionMap.put(ProviderTableMeta.FILE_CONTENT_LENGTH,
                ProviderTableMeta.FILE_CONTENT_LENGTH);
        mProjectionMap.put(ProviderTableMeta.FILE_CONTENT_TYPE,
                ProviderTableMeta.FILE_CONTENT_TYPE);
        mProjectionMap.put(ProviderTableMeta.FILE_STORAGE_PATH,
                ProviderTableMeta.FILE_STORAGE_PATH);
        mProjectionMap.put(ProviderTableMeta.FILE_LAST_SYNC_DATE,
                ProviderTableMeta.FILE_LAST_SYNC_DATE);
        mProjectionMap.put(ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA,
                ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA);
        mProjectionMap.put(ProviderTableMeta.FILE_KEEP_IN_SYNC,
                ProviderTableMeta.FILE_KEEP_IN_SYNC);
        mProjectionMap.put(ProviderTableMeta.FILE_ACCOUNT_OWNER,
                ProviderTableMeta.FILE_ACCOUNT_OWNER);
        mProjectionMap.put(ProviderTableMeta.FILE_ETAG, 
                ProviderTableMeta.FILE_ETAG);
    }

    private static final int SINGLE_FILE = 1;
    private static final int DIRECTORY = 2;
    private static final int ROOT_DIRECTORY = 3;

    private UriMatcher mUriMatcher;
    
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        //Log_OC.d(TAG, "Deleting " + uri + " at provider " + this);
        int count = 0;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = delete(db, uri, where, whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    
    
    private int delete(SQLiteDatabase db, Uri uri, String where, String[] whereArgs) {
        int count = 0;
        switch (mUriMatcher.match(uri)) {
        case SINGLE_FILE:
            /*Cursor c = query(db, uri, null, where, whereArgs, null);
            String remotePath = "(unexisting)";
            if (c != null && c.moveToFirst()) {
                remotePath = c.getString(c.getColumnIndex(ProviderTableMeta.FILE_PATH));
            }
            Log_OC.d(TAG, "Removing FILE " + remotePath);
            */
            count = db.delete(ProviderTableMeta.DB_NAME,
                    ProviderTableMeta._ID
                            + "="
                            + uri.getPathSegments().get(1)
                            + (!TextUtils.isEmpty(where) ? " AND (" + where
                                    + ")" : ""), whereArgs);
            /* just for log
            if (c!=null) {
                c.close();
            }
            */
            break;
        case DIRECTORY:
            // deletion of folder is recursive
            /*
            Uri folderUri = ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, Long.parseLong(uri.getPathSegments().get(1)));
            Cursor folder = query(db, folderUri, null, null, null, null);
            String folderName = "(unknown)";
            if (folder != null && folder.moveToFirst()) {
                folderName = folder.getString(folder.getColumnIndex(ProviderTableMeta.FILE_PATH));
            }
            */
            Cursor children = query(uri, null, null, null, null);
            if (children != null && children.moveToFirst())  {
                long childId;
                boolean isDir; 
                //String remotePath; 
                while (!children.isAfterLast()) {
                    childId = children.getLong(children.getColumnIndex(ProviderTableMeta._ID));
                    isDir = "DIR".equals(children.getString(children.getColumnIndex(ProviderTableMeta.FILE_CONTENT_TYPE)));
                    //remotePath = children.getString(children.getColumnIndex(ProviderTableMeta.FILE_PATH));
                    if (isDir) {
                        count += delete(db, ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_DIR, childId), null, null);
                    } else {
                        count += delete(db, ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, childId), null, null);
                    }
                    children.moveToNext();
                }
                children.close();
            } /*else {
                Log_OC.d(TAG, "No child to remove in DIRECTORY " + folderName);
            }
            Log_OC.d(TAG, "Removing DIRECTORY " + folderName + " (or maybe not) ");
            */
            count += db.delete(ProviderTableMeta.DB_NAME,
                    ProviderTableMeta._ID
                    + "="
                    + uri.getPathSegments().get(1)
                    + (!TextUtils.isEmpty(where) ? " AND (" + where
                            + ")" : ""), whereArgs);
            /* Just for log
             if (folder != null) {
                folder.close();
            }*/
            break;
        case ROOT_DIRECTORY:
            //Log_OC.d(TAG, "Removing ROOT!");
            count = db.delete(ProviderTableMeta.DB_NAME, where, whereArgs);
            break;
        default:
            //Log_OC.e(TAG, "Unknown uri " + uri);
            throw new IllegalArgumentException("Unknown uri: " + uri.toString());
        }
        return count;
    }
    

    @Override
    public String getType(Uri uri) {
        switch (mUriMatcher.match(uri)) {
        case ROOT_DIRECTORY:
            return ProviderTableMeta.CONTENT_TYPE;
        case SINGLE_FILE:
            return ProviderTableMeta.CONTENT_TYPE_ITEM;
        default:
            throw new IllegalArgumentException("Unknown Uri id."
                    + uri.toString());
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        //Log_OC.d(TAG, "Inserting " + values.getAsString(ProviderTableMeta.FILE_PATH) + " at provider " + this);
        Uri newUri = null;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            newUri = insert(db, uri, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(newUri, null);
        return newUri;
    }
    
    private Uri insert(SQLiteDatabase db, Uri uri, ContentValues values) {
        if (mUriMatcher.match(uri) != SINGLE_FILE &&
                mUriMatcher.match(uri) != ROOT_DIRECTORY) {
            //Log_OC.e(TAG, "Inserting invalid URI: " + uri);
            throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

        String remotePath = values.getAsString(ProviderTableMeta.FILE_PATH);
        String accountName = values.getAsString(ProviderTableMeta.FILE_ACCOUNT_OWNER);
        String[] projection = new String[] {ProviderTableMeta._ID, ProviderTableMeta.FILE_PATH, ProviderTableMeta.FILE_ACCOUNT_OWNER };
        String where = ProviderTableMeta.FILE_PATH + "=? AND " + ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?";
        String[] whereArgs = new String[] {remotePath, accountName};
        Cursor doubleCheck = query(db, uri, projection, where, whereArgs, null);
        if (doubleCheck == null || !doubleCheck.moveToFirst()) {    // ugly patch; serious refactorization is needed to reduce work in FileDataStorageManager and bring it to FileContentProvider 
            long rowId = db.insert(ProviderTableMeta.DB_NAME, null, values);
            if (rowId > 0) {
                Uri insertedFileUri = ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, rowId);
                //Log_OC.d(TAG, "Inserted " + values.getAsString(ProviderTableMeta.FILE_PATH) + " at provider " + this);
                return insertedFileUri;
            } else {
                //Log_OC.d(TAG, "Error while inserting " + values.getAsString(ProviderTableMeta.FILE_PATH)  + " at provider " + this);
                throw new SQLException("ERROR " + uri);
            }
        } else {
            // file is already inserted; race condition, let's avoid a duplicated entry
            Uri insertedFileUri = ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, doubleCheck.getLong(doubleCheck.getColumnIndex(ProviderTableMeta._ID)));
            doubleCheck.close();
            return insertedFileUri;
        }
    }

    
    @Override
    public boolean onCreate() {
        mDbHelper = new DataBaseHelper(getContext());
        
        String authority = getContext().getResources().getString(R.string.authority);
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(authority, null, ROOT_DIRECTORY);
        mUriMatcher.addURI(authority, "file/", SINGLE_FILE);
        mUriMatcher.addURI(authority, "file/#", SINGLE_FILE);
        mUriMatcher.addURI(authority, "dir/", DIRECTORY);
        mUriMatcher.addURI(authority, "dir/#", DIRECTORY);
        
        return true;
    }

    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor result = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        db.beginTransaction();
        try {
            result = query(db, uri, projection, selection, selectionArgs, sortOrder);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }
    
    private Cursor query(SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder sqlQuery = new SQLiteQueryBuilder();

        sqlQuery.setTables(ProviderTableMeta.DB_NAME);
        sqlQuery.setProjectionMap(mProjectionMap);

        switch (mUriMatcher.match(uri)) {
        case ROOT_DIRECTORY:
            break;
        case DIRECTORY:
            String folderId = uri.getPathSegments().get(1);
            sqlQuery.appendWhere(ProviderTableMeta.FILE_PARENT + "="
                    + folderId);
            break;
        case SINGLE_FILE:
            if (uri.getPathSegments().size() > 1) {
                sqlQuery.appendWhere(ProviderTableMeta._ID + "="
                        + uri.getPathSegments().get(1));
            }
            break;
        default:
            throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

        String order;
        if (TextUtils.isEmpty(sortOrder)) {
            order = ProviderTableMeta.DEFAULT_SORT_ORDER;
        } else {
            order = sortOrder;
        }

        // DB case_sensitive
        db.execSQL("PRAGMA case_sensitive_like = true");
        Cursor c = sqlQuery.query(db, projection, selection, selectionArgs, null, null, order);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        
        //Log_OC.d(TAG, "Updating " + values.getAsString(ProviderTableMeta.FILE_PATH) + " at provider " + this);
        int count = 0;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = update(db, uri, values, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    
    
    private int update(SQLiteDatabase db, Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        switch (mUriMatcher.match(uri)) {
            case DIRECTORY:
                return updateFolderSize(db, selectionArgs[0]);
            default:
                return db.update(ProviderTableMeta.DB_NAME, values, selection, selectionArgs);
        }
    }    

    
    private int updateFolderSize(SQLiteDatabase db, String folderId) {
        int count = 0;
        String [] whereArgs = new String[] { folderId };
        
        // read current size saved for the folder 
        long folderSize = 0;
        long folderParentId = -1;
        Uri selectFolderUri = Uri.withAppendedPath(ProviderTableMeta.CONTENT_URI_FILE, folderId);
        String[] folderProjection = new String[] { ProviderTableMeta.FILE_CONTENT_LENGTH,  ProviderTableMeta.FILE_PARENT};
        String folderWhere = ProviderTableMeta._ID + "=?";
        Cursor folderCursor = query(db, selectFolderUri, folderProjection, folderWhere, whereArgs, null);
        if (folderCursor != null && folderCursor.moveToFirst()) {
            folderSize = folderCursor.getLong(folderCursor.getColumnIndex(ProviderTableMeta.FILE_CONTENT_LENGTH));;
            folderParentId = folderCursor.getLong(folderCursor.getColumnIndex(ProviderTableMeta.FILE_PARENT));;
        }
        folderCursor.close();
        
        // read and sum sizes of children
        long childrenSize = 0;
        Uri selectChildrenUri = Uri.withAppendedPath(ProviderTableMeta.CONTENT_URI_DIR, folderId);
        String[] childrenProjection = new String[] { ProviderTableMeta.FILE_CONTENT_LENGTH,  ProviderTableMeta.FILE_PARENT};
        String childrenWhere = ProviderTableMeta.FILE_PARENT + "=?";
        Cursor childrenCursor = query(db, selectChildrenUri, childrenProjection, childrenWhere, whereArgs, null);
        if (childrenCursor != null && childrenCursor.moveToFirst()) {
            while (!childrenCursor.isAfterLast()) {
                childrenSize += childrenCursor.getLong(childrenCursor.getColumnIndex(ProviderTableMeta.FILE_CONTENT_LENGTH));
                childrenCursor.moveToNext();
            }
        }
        childrenCursor.close();
        
        // update if needed
        if (folderSize != childrenSize) {
            Log_OC.d("FileContentProvider", "Updating " + folderSize + " to " + childrenSize);
            ContentValues cv = new ContentValues();
            cv.put(ProviderTableMeta.FILE_CONTENT_LENGTH, childrenSize);
            count = db.update(ProviderTableMeta.DB_NAME, cv, folderWhere, whereArgs);
            
            // propagate update until root
            if (folderParentId > FileDataStorageManager.ROOT_PARENT_ID) {
                Log_OC.d("FileContentProvider", "Propagating update to " + folderParentId);
                updateFolderSize(db, String.valueOf(folderParentId));
            } else {
                Log_OC.d("FileContentProvider", "NOT propagating to " + folderParentId);
            }
        } else {
            Log_OC.d("FileContentProvider", "NOT updating, sizes are " + folderSize + " and " + childrenSize);
        }
        return count;
    }

    
    @Override
    public ContentProviderResult[] applyBatch (ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        Log_OC.d("FileContentProvider", "applying batch in provider " + this + " (temporary: " + isTemporary() + ")" );
        ContentProviderResult[] results = new ContentProviderResult[operations.size()];
        int i=0;
        
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();  // it's supposed that transactions can be nested
        try {
            for (ContentProviderOperation operation : operations) {
                results[i] = operation.apply(this, results, i);
                i++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log_OC.d("FileContentProvider", "applied batch in provider " + this);
        return results;
    }


    class DataBaseHelper extends SQLiteOpenHelper {

        public DataBaseHelper(Context context) {
            super(context, ProviderMeta.DB_NAME, null, ProviderMeta.DB_VERSION);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // files table
            Log_OC.i("SQL", "Entering in onCreate");
            db.execSQL("CREATE TABLE " + ProviderTableMeta.DB_NAME + "("
                    + ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                    + ProviderTableMeta.FILE_NAME + " TEXT, "
                    + ProviderTableMeta.FILE_PATH + " TEXT, "
                    + ProviderTableMeta.FILE_PARENT + " INTEGER, "
                    + ProviderTableMeta.FILE_CREATION + " INTEGER, "
                    + ProviderTableMeta.FILE_MODIFIED + " INTEGER, "
                    + ProviderTableMeta.FILE_CONTENT_TYPE + " TEXT, "
                    + ProviderTableMeta.FILE_CONTENT_LENGTH + " INTEGER, "
                    + ProviderTableMeta.FILE_STORAGE_PATH + " TEXT, "
                    + ProviderTableMeta.FILE_ACCOUNT_OWNER + " TEXT, "
                    + ProviderTableMeta.FILE_LAST_SYNC_DATE + " INTEGER, "
                    + ProviderTableMeta.FILE_KEEP_IN_SYNC + " INTEGER, "
                    + ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + " INTEGER, "
                    + ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + " INTEGER, "
                    + ProviderTableMeta.FILE_ETAG + " TEXT );"
                    );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log_OC.i("SQL", "Entering in onUpgrade");
            boolean upgraded = false; 
            if (oldVersion == 1 && newVersion >= 2) {
                Log_OC.i("SQL", "Entering in the #1 ADD in onUpgrade");
                db.execSQL("ALTER TABLE " + ProviderTableMeta.DB_NAME +
                           " ADD COLUMN " + ProviderTableMeta.FILE_KEEP_IN_SYNC  + " INTEGER " +
                           " DEFAULT 0");
                upgraded = true;
            }
            if (oldVersion < 3 && newVersion >= 3) {
                Log_OC.i("SQL", "Entering in the #2 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderTableMeta.DB_NAME +
                               " ADD COLUMN " + ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA  + " INTEGER " +
                               " DEFAULT 0");
                    
                    // assume there are not local changes pending to upload
                    db.execSQL("UPDATE " + ProviderTableMeta.DB_NAME + 
                            " SET " + ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + " = " + System.currentTimeMillis() + 
                            " WHERE " + ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");
                 
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 4 && newVersion >= 4) {
                Log_OC.i("SQL", "Entering in the #3 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db .execSQL("ALTER TABLE " + ProviderTableMeta.DB_NAME +
                           " ADD COLUMN " + ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA  + " INTEGER " +
                           " DEFAULT 0");
                
                    db.execSQL("UPDATE " + ProviderTableMeta.DB_NAME + 
                           " SET " + ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + " = " + ProviderTableMeta.FILE_MODIFIED + 
                           " WHERE " + ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");
                
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion + ", newVersion == " + newVersion);
        
            if (oldVersion < 5 && newVersion >= 5) {
                Log_OC.i("SQL", "Entering in the #4 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db .execSQL("ALTER TABLE " + ProviderTableMeta.DB_NAME +
                            " ADD COLUMN " + ProviderTableMeta.FILE_ETAG + " TEXT " +
                            " DEFAULT NULL");
                    
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion + ", newVersion == " + newVersion);
        }
    }

}

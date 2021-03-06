package com.huawei.hwsqlite;

import android.annotation.SuppressLint;
import android.database.CursorWindow;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;
import android.util.Printer;
import com.huawei.android.app.admin.DeviceSettingsManager;
import com.huawei.hwsqlite.SQLiteDebug;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SQLiteConnection implements CancellationSignal.OnCancelListener {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    private static final boolean DEBUG = false;
    /* access modifiers changed from: private */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String TAG = "SQLiteConnection";
    private Set<String> attachedAlias;
    private int mCancellationSignalAttachCount;
    private final SQLiteCloseGuard mCloseGuard = SQLiteCloseGuard.get();
    private final SQLiteDatabaseConfiguration mConfiguration;
    private final int mConnectionId;
    private long mConnectionPtr;
    private final boolean mIsPrimaryConnection;
    private final boolean mIsReadOnlyConnection;
    private boolean mOnlyAllowReadOnlyOperations;
    private final SQLiteConnectionPool mPool;
    private final PreparedStatementCache mPreparedStatementCache;
    private PreparedStatement mPreparedStatementPool;
    private final OperationLog mRecentOperations;
    private PreparedStatement mStepQueryStatement;
    private boolean mStepQueryStatementBindArgs;

    private static final class Operation {
        public ArrayList<Object> mBindArgs;
        public int mCookie;
        public long mEndTime;
        public Exception mException;
        public boolean mFinished;
        public String mKind;
        public String mSql;
        public long mStartTime;
        public long mStartWallTime;

        private Operation() {
        }

        public void describe(StringBuilder msg, boolean verbose) {
            msg.append(this.mKind);
            if (this.mFinished) {
                msg.append(" took ");
                msg.append(this.mEndTime - this.mStartTime);
                msg.append("ms");
            } else {
                msg.append(" started ");
                msg.append(System.currentTimeMillis() - this.mStartWallTime);
                msg.append("ms ago");
            }
            msg.append(" - ");
            msg.append(getStatus());
            if (this.mSql != null) {
                msg.append(", sql=\"");
                msg.append(SQLiteConnection.trimSqlForDisplay(this.mSql));
                msg.append("\"");
            }
            if (!(!verbose || this.mBindArgs == null || this.mBindArgs.size() == 0)) {
                msg.append(", bindArgs=[");
                int count = this.mBindArgs.size();
                for (int i = 0; i < count; i++) {
                    Object arg = this.mBindArgs.get(i);
                    if (i != 0) {
                        msg.append(", ");
                    }
                    if (arg == null) {
                        msg.append("null");
                    } else if (arg instanceof byte[]) {
                        msg.append("<byte[]>");
                    } else if (arg instanceof String) {
                        msg.append("\"");
                        msg.append((String) arg);
                        msg.append("\"");
                    } else {
                        msg.append(arg);
                    }
                }
                msg.append("]");
            }
            if (this.mException != null) {
                msg.append(", exception=\"");
                msg.append(this.mException.getMessage());
                msg.append("\"");
            }
        }

        private String getStatus() {
            if (!this.mFinished) {
                return "running";
            }
            return this.mException != null ? "failed" : "succeeded";
        }

        /* access modifiers changed from: private */
        public String getFormattedStartTime() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(this.mStartWallTime));
        }
    }

    private static final class OperationLog {
        private static final int COOKIE_GENERATION_SHIFT = 8;
        private static final int COOKIE_INDEX_MASK = 255;
        private static final int MAX_RECENT_OPERATIONS = 20;
        private int mGeneration;
        private int mIndex;
        private final Operation[] mOperations;

        private OperationLog() {
            this.mOperations = new Operation[20];
        }

        private static boolean logOperationEnabled() {
            return SQLiteGlobal.getSlowQueryThreshold() >= 0;
        }

        public int beginOperation(String kind, String sql, Object[] bindArgs) {
            int i;
            if (!logOperationEnabled()) {
                return -256;
            }
            synchronized (this.mOperations) {
                int index = (this.mIndex + 1) % 20;
                Operation operation = this.mOperations[index];
                if (operation == null) {
                    operation = new Operation();
                    this.mOperations[index] = operation;
                } else {
                    operation.mFinished = false;
                    operation.mException = null;
                    if (operation.mBindArgs != null) {
                        operation.mBindArgs.clear();
                    }
                }
                operation.mStartWallTime = System.currentTimeMillis();
                operation.mStartTime = SystemClock.uptimeMillis();
                operation.mKind = kind;
                operation.mSql = sql;
                if (bindArgs != null) {
                    if (operation.mBindArgs == null) {
                        operation.mBindArgs = new ArrayList<>();
                    } else {
                        operation.mBindArgs.clear();
                    }
                    for (Object arg : bindArgs) {
                        if (arg == null || !(arg instanceof byte[])) {
                            operation.mBindArgs.add(arg);
                        } else {
                            operation.mBindArgs.add(SQLiteConnection.EMPTY_BYTE_ARRAY);
                        }
                    }
                }
                operation.mCookie = newOperationCookieLocked(index);
                this.mIndex = index;
                i = operation.mCookie;
            }
            return i;
        }

        public void failOperation(int cookie, Exception ex) {
            if (cookie >= 0) {
                synchronized (this.mOperations) {
                    Operation operation = getOperationLocked(cookie);
                    if (operation != null) {
                        operation.mException = ex;
                    }
                }
            }
        }

        public void endOperation(int cookie) {
            if (cookie >= 0) {
                synchronized (this.mOperations) {
                    if (endOperationDeferLogLocked(cookie)) {
                        logOperationLocked(cookie, null);
                    }
                }
            }
        }

        public boolean endOperationDeferLog(int cookie) {
            boolean endOperationDeferLogLocked;
            if (cookie < 0) {
                return false;
            }
            synchronized (this.mOperations) {
                endOperationDeferLogLocked = endOperationDeferLogLocked(cookie);
            }
            return endOperationDeferLogLocked;
        }

        public void logOperation(int cookie, String detail) {
            if (cookie >= 0) {
                synchronized (this.mOperations) {
                    logOperationLocked(cookie, detail);
                }
            }
        }

        private boolean endOperationDeferLogLocked(int cookie) {
            Operation operation = getOperationLocked(cookie);
            if (operation == null) {
                return false;
            }
            operation.mEndTime = SystemClock.uptimeMillis();
            operation.mFinished = true;
            return SQLiteDebug.shouldLogSlowQuery(operation.mEndTime - operation.mStartTime);
        }

        private void logOperationLocked(int cookie, String detail) {
            Operation operation = getOperationLocked(cookie);
            if (operation != null) {
                StringBuilder msg = new StringBuilder();
                operation.describe(msg, false);
                if (detail != null) {
                    msg.append(", ");
                    msg.append(detail);
                }
                Log.d(SQLiteConnection.TAG, msg.toString());
            }
        }

        private int newOperationCookieLocked(int index) {
            if ((this.mGeneration << 8) < 0) {
                this.mGeneration = 0;
            }
            int generation = this.mGeneration;
            this.mGeneration = generation + 1;
            return (generation << 8) | index;
        }

        private Operation getOperationLocked(int cookie) {
            Operation operation = this.mOperations[cookie & 255];
            if (operation.mCookie == cookie) {
                return operation;
            }
            return null;
        }

        /* JADX WARNING: Code restructure failed: missing block: B:14:0x0027, code lost:
            return null;
         */
        public String describeCurrentOperation() {
            if (!logOperationEnabled()) {
                return null;
            }
            synchronized (this.mOperations) {
                Operation operation = this.mOperations[this.mIndex];
                if (operation != null && !operation.mFinished) {
                    StringBuilder msg = new StringBuilder();
                    operation.describe(msg, false);
                    String sb = msg.toString();
                    return sb;
                }
            }
        }

        public void dump(Printer printer, boolean verbose) {
            if (logOperationEnabled()) {
                synchronized (this.mOperations) {
                    printer.println("  Most recently executed operations:");
                    int index = this.mIndex;
                    Operation operation = this.mOperations[index];
                    if (operation != null) {
                        int n = 0;
                        do {
                            StringBuilder msg = new StringBuilder();
                            msg.append("    ");
                            msg.append(n);
                            msg.append(": [");
                            msg.append(operation.getFormattedStartTime());
                            msg.append("] ");
                            operation.describe(msg, verbose);
                            printer.println(msg.toString());
                            if (index > 0) {
                                index--;
                            } else {
                                index = 19;
                            }
                            n++;
                            operation = this.mOperations[index];
                            if (operation == null) {
                                break;
                            }
                        } while (n < 20);
                    } else {
                        printer.println("    <none>");
                    }
                }
            }
        }
    }

    public static final class PreparedStatement {
        public boolean mInCache;
        public boolean mInUse;
        public int mNumParameters;
        public PreparedStatement mPoolNext;
        public boolean mReadOnly;
        public String mSql;
        public long mStatementPtr;
        public int mType;
    }

    private final class PreparedStatementCache extends LruCache<String, PreparedStatement> {
        public PreparedStatementCache(int size) {
            super(size);
        }

        /* access modifiers changed from: protected */
        public void entryRemoved(boolean evicted, String key, PreparedStatement oldValue, PreparedStatement newValue) {
            oldValue.mInCache = false;
            if (!oldValue.mInUse) {
                SQLiteConnection.this.finalizePreparedStatement(oldValue);
            }
        }

        public void dump(Printer printer) {
            printer.println("  Prepared statement cache:");
            Map<String, PreparedStatement> cache = snapshot();
            if (!cache.isEmpty()) {
                int i = 0;
                for (Map.Entry<String, PreparedStatement> entry : cache.entrySet()) {
                    PreparedStatement statement = entry.getValue();
                    if (statement.mInCache) {
                        printer.println("    " + i + ": statementPtr=0x" + Long.toHexString(statement.mStatementPtr) + ", numParameters=" + statement.mNumParameters + ", type=" + statement.mType + ", readOnly=" + statement.mReadOnly + ", sql=\"" + SQLiteConnection.trimSqlForDisplay(entry.getKey()) + "\"");
                    }
                    i++;
                }
                return;
            }
            printer.println("    <none>");
        }
    }

    private static native void nativeBindBlob(long j, long j2, int i, byte[] bArr);

    private static native void nativeBindDouble(long j, long j2, int i, double d);

    private static native void nativeBindLong(long j, long j2, int i, long j3);

    private static native void nativeBindNull(long j, long j2, int i);

    private static native void nativeBindString(long j, long j2, int i, String str);

    private static native void nativeCancel(long j);

    private static native void nativeClose(long j);

    private static native void nativeExecute(long j, long j2);

    private static native int nativeExecuteForBlobFileDescriptor(long j, long j2);

    private static native int nativeExecuteForChangedRowCount(long j, long j2);

    private static native long nativeExecuteForCursorWindow(long j, long j2, CursorWindow cursorWindow, int i, int i2, boolean z);

    private static native long nativeExecuteForLastInsertedRowId(long j, long j2);

    private static native long nativeExecuteForLong(long j, long j2);

    private static native int nativeExecuteForStepQuery(long j, long j2);

    private static native String nativeExecuteForString(long j, long j2);

    private static native void nativeFinalizeStatement(long j, long j2);

    private static native int nativeGetColumnCount(long j, long j2);

    private static native String nativeGetColumnName(long j, long j2, int i);

    private static native int nativeGetDbLookaside(long j);

    private static native int nativeGetParameterCount(long j, long j2);

    private static native boolean nativeIsReadOnly(long j, long j2);

    private static native void nativeKey(long j, byte[] bArr);

    private static native long nativeOpen(String str, int i, String str2, boolean z, boolean z2);

    private static native long nativePrepareStatement(long j, String str);

    private static native void nativeRegisterCustomFunction(long j, SQLiteCustomFunction sQLiteCustomFunction);

    private static native void nativeRegisterLocalizedCollators(long j, String str);

    private static native void nativeRekey(long j, byte[] bArr);

    private static native void nativeResetCancel(long j, boolean z);

    private static native void nativeResetStatementAndClearBindings(long j, long j2);

    private SQLiteConnection(SQLiteConnectionPool pool, SQLiteDatabaseConfiguration configuration, int connectionId, boolean primaryConnection) {
        boolean z = false;
        this.mStepQueryStatementBindArgs = false;
        this.mRecentOperations = new OperationLog();
        this.attachedAlias = new HashSet();
        this.mPool = pool;
        this.mConfiguration = new SQLiteDatabaseConfiguration(configuration);
        this.mConnectionId = connectionId;
        this.mIsPrimaryConnection = primaryConnection;
        this.mIsReadOnlyConnection = (configuration.openFlags & 1) != 0 ? true : z;
        this.mPreparedStatementCache = new PreparedStatementCache(this.mConfiguration.maxSqlCacheSize);
        this.mCloseGuard.open("close");
    }

    /* access modifiers changed from: protected */
    public void finalize() throws Throwable {
        try {
            if (!(this.mPool == null || this.mConnectionPtr == 0)) {
                this.mPool.onConnectionLeaked();
            }
            dispose(true);
        } finally {
            super.finalize();
        }
    }

    static SQLiteConnection open(SQLiteConnectionPool pool, SQLiteDatabaseConfiguration configuration, int connectionId, boolean primaryConnection) {
        SQLiteConnection connection = new SQLiteConnection(pool, configuration, connectionId, primaryConnection);
        try {
            connection.open();
            return connection;
        } catch (SQLiteException ex) {
            connection.dispose(false);
            throw ex;
        }
    }

    /* access modifiers changed from: package-private */
    public void close() {
        dispose(false);
    }

    private void setEncryptKey() {
        byte[] encryptKey = this.mConfiguration.getEncryptKey();
        if (encryptKey != null && encryptKey.length != 0) {
            try {
                nativeKey(this.mConnectionPtr, encryptKey);
                for (int i = 0; i < encryptKey.length; i++) {
                    encryptKey[i] = 0;
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Failed to set key");
                throw ex;
            } catch (Throwable th) {
                for (int i2 = 0; i2 < encryptKey.length; i2++) {
                    encryptKey[i2] = 0;
                }
                throw th;
            }
        }
    }

    private void open() {
        this.mConnectionPtr = nativeOpen(this.mConfiguration.path, this.mConfiguration.openFlags, this.mConfiguration.label, SQLiteDebug.DEBUG_SQL_STATEMENTS, SQLiteDebug.DEBUG_SQL_TIME);
        setPageSize();
        setEncryptKey();
        setForeignKeyModeFromConfiguration();
        setWalModeFromConfiguration();
        setJournalSizeLimit();
        setAutoCheckpointInterval();
        setLocaleFromConfiguration();
        setAttachAlias();
        int functionCount = this.mConfiguration.customFunctions.size();
        for (int i = 0; i < functionCount; i++) {
            nativeRegisterCustomFunction(this.mConnectionPtr, this.mConfiguration.customFunctions.get(i));
        }
    }

    private void dispose(boolean finalized) {
        if (this.mCloseGuard != null) {
            if (finalized) {
                this.mCloseGuard.warnIfOpen();
            }
            this.mCloseGuard.close();
        }
        if (this.mConnectionPtr != 0) {
            if (this.mStepQueryStatement != null) {
                releasePreparedStatement(this.mStepQueryStatement);
            }
            int cookie = this.mRecentOperations.beginOperation("close", null, null);
            try {
                this.mPreparedStatementCache.evictAll();
                nativeClose(this.mConnectionPtr);
                this.mConnectionPtr = 0;
            } finally {
                this.mRecentOperations.endOperation(cookie);
            }
        }
    }

    private void setPageSize() {
        if (!this.mConfiguration.isInMemoryDb() && !this.mIsReadOnlyConnection) {
            long newValue = (long) SQLiteGlobal.getDefaultPageSize();
            if (executeForLong("PRAGMA page_size", null, null) != newValue) {
                execute("PRAGMA page_size=" + newValue, null, null);
            }
        }
    }

    private void setAutoCheckpointInterval() {
        if (!this.mConfiguration.isInMemoryDb() && !this.mIsReadOnlyConnection) {
            long newValue = (long) SQLiteGlobal.getWALAutoCheckpoint();
            if (executeForLong("PRAGMA wal_autocheckpoint", null, null) != newValue) {
                executeForLong("PRAGMA wal_autocheckpoint=" + newValue, null, null);
            }
        }
    }

    private void setJournalSizeLimit() {
        if (!this.mConfiguration.isInMemoryDb() && !this.mIsReadOnlyConnection) {
            long newValue = (long) SQLiteGlobal.getJournalSizeLimit();
            if (executeForLong("PRAGMA journal_size_limit", null, null) != newValue) {
                executeForLong("PRAGMA journal_size_limit=" + newValue, null, null);
            }
        }
    }

    private void setForeignKeyModeFromConfiguration() {
        if (!this.mIsReadOnlyConnection) {
            long newValue = this.mConfiguration.foreignKeyConstraintsEnabled ? 1 : 0;
            if (executeForLong("PRAGMA foreign_keys", null, null) != newValue) {
                execute("PRAGMA foreign_keys=" + newValue, null, null);
            }
        }
    }

    private void setWalModeFromConfiguration() {
        if (!this.mConfiguration.isInMemoryDb() && !this.mIsReadOnlyConnection) {
            if ((this.mConfiguration.openFlags & 536870912) != 0) {
                setJournalMode("WAL");
                setSyncMode(SQLiteGlobal.getWALSyncMode());
                return;
            }
            setJournalMode(SQLiteGlobal.getDefaultJournalMode());
            setSyncMode(SQLiteGlobal.getDefaultSyncMode());
        }
    }

    private void setSyncMode(String newValue) {
        if (!canonicalizeSyncMode(executeForString("PRAGMA synchronous", null, null)).equalsIgnoreCase(canonicalizeSyncMode(newValue))) {
            execute("PRAGMA synchronous=" + newValue, null, null);
        }
    }

    private static String canonicalizeSyncMode(String value) {
        if (value.equals("0")) {
            return "OFF";
        }
        if (value.equals("1")) {
            return DeviceSettingsManager.CONFIG_NORMAL_VALUE;
        }
        if (value.equals("2")) {
            return "FULL";
        }
        return value;
    }

    private void setJournalMode(String newValue) {
        String value = executeForString("PRAGMA journal_mode", null, null);
        if (!value.equalsIgnoreCase(newValue)) {
            try {
                if (executeForString("PRAGMA journal_mode=" + newValue, null, null).equalsIgnoreCase(newValue)) {
                    return;
                }
            } catch (SQLiteDatabaseLockedException e) {
            }
            Log.w(TAG, "Could not change the database journal mode of '" + this.mConfiguration.label + "' from '" + value + "' to '" + newValue + "' because the database is locked.  This usually means that there are other open connections to the database which prevents the database from enabling or disabling write-ahead logging mode.  Proceeding without changing the journal mode.");
        }
    }

    private void setLocaleFromConfiguration() {
        if ((this.mConfiguration.openFlags & 16) == 0) {
            String newLocale = this.mConfiguration.locale.toString();
            nativeRegisterLocalizedCollators(this.mConnectionPtr, newLocale);
            if (!this.mIsReadOnlyConnection) {
                try {
                    execute("CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)", null, null);
                    String oldLocale = executeForString("SELECT locale FROM android_metadata UNION SELECT NULL ORDER BY locale DESC LIMIT 1", null, null);
                    if (oldLocale == null || !oldLocale.equals(newLocale)) {
                        execute("BEGIN", null, null);
                        execute("DELETE FROM android_metadata", null, null);
                        execute("INSERT INTO android_metadata (locale) VALUES(?)", new Object[]{newLocale}, null);
                        execute("REINDEX LOCALIZED", null, null);
                        execute(1 != 0 ? "COMMIT" : "ROLLBACK", null, null);
                    }
                } catch (RuntimeException ex) {
                    Log.w(TAG, "Failed to change locale for db '" + this.mConfiguration.label + "' to '" + newLocale + "'.", ex);
                } catch (Throwable th) {
                    execute(0 != 0 ? "COMMIT" : "ROLLBACK", null, null);
                    throw th;
                }
            }
        }
    }

    private void setAttachAlias() {
        attachAlias();
        detachAlias();
    }

    /* JADX WARNING: Removed duplicated region for block: B:20:0x0061  */
    private void attachAlias() {
        SQLiteAttached attaching;
        try {
            int length = this.mConfiguration.attachedAlias.size();
            attaching = null;
            int i = 0;
            while (i < length) {
                try {
                    SQLiteAttached attached = this.mConfiguration.attachedAlias.get(i);
                    if (!this.attachedAlias.contains(attached.alias)) {
                        attaching = attached;
                        if (attached.encryptKey != null) {
                            execute("ATTACH DATABASE ? AS ? KEY ?", new Object[]{attached.path, attached.alias, attached.encryptKey}, null);
                        } else {
                            execute("ATTACH DATABASE ? AS ?", new String[]{attached.path, attached.alias}, null);
                        }
                        this.attachedAlias.add(attached.alias);
                    }
                    i++;
                } catch (RuntimeException e) {
                    ex = e;
                    if (attaching != null) {
                        Log.w(TAG, "Failed to attach '" + attaching.path + "' as '" + attaching.alias + "'");
                    }
                    throw new SQLiteException("attach failed", ex);
                }
            }
        } catch (RuntimeException e2) {
            ex = e2;
            attaching = null;
            if (attaching != null) {
            }
            throw new SQLiteException("attach failed", ex);
        }
    }

    private void detachAlias() {
        List<String> detached = new LinkedList<>();
        try {
            for (String alias : this.attachedAlias) {
                if (!this.mConfiguration.isAttachAliasExists(alias)) {
                    String detaching = alias;
                    execute("DETACH DATABASE ?", new String[]{alias}, null);
                    detached.add(alias);
                }
            }
            this.attachedAlias.removeAll(detached);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Failed to detach '" + null + "'");
            throw new SQLiteException("detach failed", ex);
        } catch (Throwable th) {
            this.attachedAlias.removeAll(detached);
            throw th;
        }
    }

    /* access modifiers changed from: package-private */
    public void reconfigure(SQLiteDatabaseConfiguration configuration) {
        boolean walModeChanged = false;
        this.mOnlyAllowReadOnlyOperations = false;
        int functionCount = configuration.customFunctions.size();
        for (int i = 0; i < functionCount; i++) {
            SQLiteCustomFunction function = configuration.customFunctions.get(i);
            if (!this.mConfiguration.customFunctions.contains(function)) {
                nativeRegisterCustomFunction(this.mConnectionPtr, function);
            }
        }
        boolean foreignKeyModeChanged = configuration.foreignKeyConstraintsEnabled != this.mConfiguration.foreignKeyConstraintsEnabled;
        if (((configuration.openFlags ^ this.mConfiguration.openFlags) & 536870912) != 0) {
            walModeChanged = true;
        }
        boolean localeChanged = !configuration.locale.equals(this.mConfiguration.locale);
        this.mConfiguration.updateParametersFrom(configuration);
        this.mPreparedStatementCache.resize(configuration.maxSqlCacheSize);
        if (foreignKeyModeChanged) {
            setForeignKeyModeFromConfiguration();
        }
        if (walModeChanged) {
            setWalModeFromConfiguration();
        }
        if (localeChanged) {
            setLocaleFromConfiguration();
        }
    }

    /* access modifiers changed from: package-private */
    public void setOnlyAllowReadOnlyOperations(boolean readOnly) {
        this.mOnlyAllowReadOnlyOperations = readOnly;
    }

    /* access modifiers changed from: package-private */
    public boolean isPreparedStatementInCache(String sql) {
        return this.mPreparedStatementCache.get(sql) != null;
    }

    public int getConnectionId() {
        return this.mConnectionId;
    }

    public boolean isPrimaryConnection() {
        return this.mIsPrimaryConnection;
    }

    public void prepare(String sql, SQLiteStatementInfo outStatementInfo) {
        PreparedStatement statement;
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("prepare", sql, null);
            try {
                statement = acquirePreparedStatement(sql);
                if (outStatementInfo != null) {
                    outStatementInfo.numParameters = statement.mNumParameters;
                    outStatementInfo.readOnly = statement.mReadOnly;
                    int columnCount = nativeGetColumnCount(this.mConnectionPtr, statement.mStatementPtr);
                    if (columnCount == 0) {
                        outStatementInfo.columnNames = EMPTY_STRING_ARRAY;
                    } else {
                        outStatementInfo.columnNames = new String[columnCount];
                        for (int i = 0; i < columnCount; i++) {
                            outStatementInfo.columnNames[i] = nativeGetColumnName(this.mConnectionPtr, statement.mStatementPtr, i);
                        }
                    }
                }
                releasePreparedStatement(statement);
                this.mRecentOperations.endOperation(cookie);
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th;
                }
            } catch (Throwable th2) {
                releasePreparedStatement(statement);
                throw th2;
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public void execute(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("execute", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    nativeExecute(this.mConnectionPtr, statement.mStatementPtr);
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    this.mRecentOperations.endOperation(cookie);
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public long executeForLong(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("executeForLong", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    long nativeExecuteForLong = nativeExecuteForLong(this.mConnectionPtr, statement.mStatementPtr);
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    this.mRecentOperations.endOperation(cookie);
                    return nativeExecuteForLong;
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public String executeForString(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("executeForString", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    String nativeExecuteForString = nativeExecuteForString(this.mConnectionPtr, statement.mStatementPtr);
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    this.mRecentOperations.endOperation(cookie);
                    return nativeExecuteForString;
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public ParcelFileDescriptor executeForBlobFileDescriptor(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("executeForBlobFileDescriptor", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    int fd = nativeExecuteForBlobFileDescriptor(this.mConnectionPtr, statement.mStatementPtr);
                    ParcelFileDescriptor adoptFd = fd >= 0 ? ParcelFileDescriptor.adoptFd(fd) : null;
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    this.mRecentOperations.endOperation(cookie);
                    return adoptFd;
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public int executeForChangedRowCount(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int changedRows = 0;
            int cookie = this.mRecentOperations.beginOperation("executeForChangedRowCount", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    changedRows = nativeExecuteForChangedRowCount(this.mConnectionPtr, statement.mStatementPtr);
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                        OperationLog operationLog = this.mRecentOperations;
                        operationLog.logOperation(cookie, "changedRows=" + changedRows);
                    }
                    return changedRows;
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                        OperationLog operationLog2 = this.mRecentOperations;
                        operationLog2.logOperation(cookie, "changedRows=" + changedRows);
                    }
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public long executeForLastInsertedRowId(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("executeForLastInsertedRowId", sql, bindArgs);
            try {
                PreparedStatement statement = acquirePreparedStatement(sql);
                try {
                    throwIfStatementForbidden(statement);
                    bindArguments(statement, bindArgs);
                    applyBlockGuardPolicy(statement);
                    attachCancellationSignal(cancellationSignal);
                    long nativeExecuteForLastInsertedRowId = nativeExecuteForLastInsertedRowId(this.mConnectionPtr, statement.mStatementPtr);
                    detachCancellationSignal(cancellationSignal);
                    releasePreparedStatement(statement);
                    this.mRecentOperations.endOperation(cookie);
                    return nativeExecuteForLastInsertedRowId;
                } catch (Throwable th) {
                    releasePreparedStatement(statement);
                    throw th;
                }
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th2) {
                    this.mRecentOperations.endOperation(cookie);
                    throw th2;
                }
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:67:0x00e1 A[Catch:{ all -> 0x0118 }] */
    public int executeForCursorWindow(String sql, Object[] bindArgs, CursorWindow window, int startPos, int requiredPos, boolean countAllRows, CancellationSignal cancellationSignal) {
        int filledRows;
        int countedRows;
        int cookie;
        PreparedStatement statement;
        String str = sql;
        Object[] objArr = bindArgs;
        CursorWindow cursorWindow = window;
        int i = startPos;
        CancellationSignal cancellationSignal2 = cancellationSignal;
        if (str == null) {
            throw new IllegalArgumentException("sql must not be null.");
        } else if (cursorWindow != null) {
            window.acquireReference();
            int actualPos = -1;
            int countedRows2 = -1;
            int filledRows2 = -1;
            try {
                int cookie2 = this.mRecentOperations.beginOperation("executeForCursorWindow", str, objArr);
                try {
                    PreparedStatement statement2 = acquirePreparedStatement(sql);
                    try {
                        throwIfStatementForbidden(statement2);
                        bindArguments(statement2, objArr);
                        applyBlockGuardPolicy(statement2);
                        attachCancellationSignal(cancellationSignal2);
                        try {
                            statement = statement2;
                            cookie = cookie2;
                            try {
                                long result = nativeExecuteForCursorWindow(this.mConnectionPtr, statement2.mStatementPtr, cursorWindow, i, requiredPos, countAllRows);
                                actualPos = (int) (result >> 32);
                                countedRows = (int) result;
                            } catch (Throwable th) {
                                th = th;
                                try {
                                    detachCancellationSignal(cancellationSignal2);
                                    throw th;
                                } catch (Throwable th2) {
                                    th = th2;
                                    try {
                                        releasePreparedStatement(statement);
                                        throw th;
                                    } catch (RuntimeException e) {
                                        ex = e;
                                        try {
                                            this.mRecentOperations.failOperation(cookie, ex);
                                            throw ex;
                                        } catch (Throwable th3) {
                                            ex = th3;
                                            countedRows = countedRows2;
                                            filledRows = filledRows2;
                                            if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                                            }
                                            throw ex;
                                        }
                                    }
                                }
                            }
                            try {
                                filledRows = window.getNumRows();
                                try {
                                    cursorWindow.setStartPosition(actualPos);
                                } catch (Throwable th4) {
                                    th = th4;
                                    countedRows2 = countedRows;
                                    filledRows2 = filledRows;
                                }
                            } catch (Throwable th5) {
                                th = th5;
                                countedRows2 = countedRows;
                                detachCancellationSignal(cancellationSignal2);
                                throw th;
                            }
                        } catch (Throwable th6) {
                            th = th6;
                            statement = statement2;
                            cookie = cookie2;
                            detachCancellationSignal(cancellationSignal2);
                            throw th;
                        }
                        try {
                            detachCancellationSignal(cancellationSignal2);
                            try {
                                releasePreparedStatement(statement);
                                if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                                    this.mRecentOperations.logOperation(cookie, "window='" + cursorWindow + "', startPos=" + i + ", actualPos=" + actualPos + ", filledRows=" + filledRows + ", countedRows=" + countedRows);
                                }
                                return countedRows;
                            } catch (RuntimeException e2) {
                                ex = e2;
                                countedRows2 = countedRows;
                                filledRows2 = filledRows;
                                this.mRecentOperations.failOperation(cookie, ex);
                                throw ex;
                            } catch (Throwable th7) {
                                ex = th7;
                                if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                                }
                                throw ex;
                            }
                        } catch (Throwable th8) {
                            th = th8;
                            countedRows2 = countedRows;
                            filledRows2 = filledRows;
                            releasePreparedStatement(statement);
                            throw th;
                        }
                    } catch (Throwable th9) {
                        th = th9;
                        statement = statement2;
                        cookie = cookie2;
                        releasePreparedStatement(statement);
                        throw th;
                    }
                } catch (RuntimeException e3) {
                    ex = e3;
                    cookie = cookie2;
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th10) {
                    ex = th10;
                    cookie = cookie2;
                    countedRows = -1;
                    filledRows = -1;
                    if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                        this.mRecentOperations.logOperation(cookie, "window='" + cursorWindow + "', startPos=" + i + ", actualPos=" + actualPos + ", filledRows=" + filledRows + ", countedRows=" + countedRows);
                    }
                    throw ex;
                }
            } finally {
                window.releaseReference();
            }
        } else {
            throw new IllegalArgumentException("window must not be null.");
        }
    }

    /* access modifiers changed from: package-private */
    public PreparedStatement beginStepQuery(String sql) {
        if (this.mStepQueryStatement == null) {
            this.mStepQueryStatement = acquirePreparedStatement(sql);
            this.mStepQueryStatementBindArgs = false;
            return this.mStepQueryStatement;
        }
        throw new IllegalStateException("begin a step query on a connection more than once");
    }

    /* access modifiers changed from: package-private */
    public void endStepQuery(PreparedStatement statement) {
        if (this.mStepQueryStatement == null) {
            throw new IllegalStateException("end a step query on a connection that never begin");
        } else if (this.mStepQueryStatement == statement) {
            releasePreparedStatement(this.mStepQueryStatement);
            this.mStepQueryStatement = null;
            this.mStepQueryStatementBindArgs = false;
        } else {
            throw new IllegalArgumentException("end a step query with an unknown statement object");
        }
    }

    /* access modifiers changed from: package-private */
    public int executeForStepQuery(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        if (sql != null) {
            int cookie = this.mRecentOperations.beginOperation("executeForStepQuery", sql, bindArgs);
            try {
                if (!this.mStepQueryStatementBindArgs) {
                    bindArguments(this.mStepQueryStatement, bindArgs);
                    this.mStepQueryStatementBindArgs = true;
                }
                attachCancellationSignal(cancellationSignal);
                int nativeExecuteForStepQuery = nativeExecuteForStepQuery(this.mConnectionPtr, this.mStepQueryStatement.mStatementPtr);
                detachCancellationSignal(cancellationSignal);
                if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                    this.mRecentOperations.logOperation(cookie, "executeForStepQuery() deferred");
                }
                return nativeExecuteForStepQuery;
            } catch (RuntimeException ex) {
                try {
                    this.mRecentOperations.failOperation(cookie, ex);
                    throw ex;
                } catch (Throwable th) {
                    if (this.mRecentOperations.endOperationDeferLog(cookie)) {
                        this.mRecentOperations.logOperation(cookie, "executeForStepQuery() deferred");
                    }
                    throw th;
                }
            } catch (Throwable th2) {
                detachCancellationSignal(cancellationSignal);
                throw th2;
            }
        } else {
            throw new IllegalArgumentException("sql must not be null.");
        }
    }

    public void changeEncryptKey(SQLiteEncryptKeyLoader newLoader) {
        if (newLoader != null) {
            byte[] newKey = newLoader.getEncryptKey();
            if (newKey == null || newKey.length == 0) {
                throw new SQLiteException("re-key failed because new key is empty");
            }
            try {
                nativeRekey(this.mConnectionPtr, newKey);
                this.mConfiguration.updateEncryptKeyLoader(newLoader);
                for (int i = 0; i < newKey.length; i++) {
                    newKey[i] = 0;
                }
            } catch (RuntimeException ex) {
                throw new SQLiteException("native re-key failed", ex);
            } catch (Throwable th) {
                for (int i2 = 0; i2 < newKey.length; i2++) {
                    newKey[i2] = 0;
                }
                throw th;
            }
        } else {
            throw new SQLiteException("re-key failed because new key loader is null");
        }
    }

    public void addAttachAlias(SQLiteAttached attached) {
        if (attached == null) {
            throw new IllegalArgumentException("attached parameter must not be null");
        } else if (this.mConfiguration.addAttachAlias(attached)) {
            attachAlias();
        } else {
            throw new SQLiteException("invalid attach parameters or conflict");
        }
    }

    public void removeAttachAlias(String alias) {
        if (alias == null || alias.length() == 0) {
            throw new IllegalArgumentException("Alias name must not be empty");
        }
        this.mConfiguration.removeAttachAlias(alias);
        detachAlias();
    }

    private PreparedStatement acquirePreparedStatement(String sql) {
        PreparedStatement statement = (PreparedStatement) this.mPreparedStatementCache.get(sql);
        boolean skipCache = false;
        if (statement != null) {
            if (!statement.mInUse) {
                return statement;
            }
            skipCache = true;
        }
        long statementPtr = nativePrepareStatement(this.mConnectionPtr, sql);
        try {
            int numParameters = nativeGetParameterCount(this.mConnectionPtr, statementPtr);
            int type = SQLiteDatabaseUtils.getSqlStatementType(sql);
            PreparedStatement statement2 = obtainPreparedStatement(sql, statementPtr, numParameters, type, nativeIsReadOnly(this.mConnectionPtr, statementPtr));
            if (!skipCache && isCacheable(type)) {
                this.mPreparedStatementCache.put(sql, statement2);
                statement2.mInCache = true;
            }
            statement2.mInUse = true;
            return statement2;
        } catch (RuntimeException ex) {
            if (statement == null || !statement.mInCache) {
                nativeFinalizeStatement(this.mConnectionPtr, statementPtr);
            }
            throw ex;
        }
    }

    private void releasePreparedStatement(PreparedStatement statement) {
        statement.mInUse = false;
        if (statement.mInCache) {
            try {
                nativeResetStatementAndClearBindings(this.mConnectionPtr, statement.mStatementPtr);
            } catch (SQLiteException e) {
                this.mPreparedStatementCache.remove(statement.mSql);
            }
        } else {
            finalizePreparedStatement(statement);
        }
    }

    /* access modifiers changed from: private */
    public void finalizePreparedStatement(PreparedStatement statement) {
        nativeFinalizeStatement(this.mConnectionPtr, statement.mStatementPtr);
        recyclePreparedStatement(statement);
    }

    private void attachCancellationSignal(CancellationSignal cancellationSignal) {
        if (cancellationSignal != null) {
            cancellationSignal.throwIfCanceled();
            this.mCancellationSignalAttachCount++;
            if (this.mCancellationSignalAttachCount == 1) {
                nativeResetCancel(this.mConnectionPtr, true);
                cancellationSignal.setOnCancelListener(this);
            }
        }
    }

    private void detachCancellationSignal(CancellationSignal cancellationSignal) {
        if (cancellationSignal != null) {
            this.mCancellationSignalAttachCount--;
            if (this.mCancellationSignalAttachCount == 0) {
                cancellationSignal.setOnCancelListener(null);
                nativeResetCancel(this.mConnectionPtr, false);
            }
        }
    }

    public void onCancel() {
        nativeCancel(this.mConnectionPtr);
    }

    private void bindArguments(PreparedStatement statement, Object[] bindArgs) {
        int count = bindArgs != null ? bindArgs.length : 0;
        if (count != statement.mNumParameters) {
            throw new SQLiteBindOrColumnIndexOutOfRangeException("Expected " + statement.mNumParameters + " bind arguments but " + count + " were provided.");
        } else if (count != 0) {
            long statementPtr = statement.mStatementPtr;
            for (int i = 0; i < count; i++) {
                Boolean bool = bindArgs[i];
                int typeOfObject = SQLiteDatabaseUtils.getTypeOfObject(bool);
                if (typeOfObject != 4) {
                    switch (typeOfObject) {
                        case 0:
                            nativeBindNull(this.mConnectionPtr, statementPtr, i + 1);
                            break;
                        case 1:
                            nativeBindLong(this.mConnectionPtr, statementPtr, i + 1, bool.longValue());
                            break;
                        case 2:
                            nativeBindDouble(this.mConnectionPtr, statementPtr, i + 1, bool.doubleValue());
                            break;
                        default:
                            if (!(bool instanceof Boolean)) {
                                nativeBindString(this.mConnectionPtr, statementPtr, i + 1, bool.toString());
                                break;
                            } else {
                                nativeBindLong(this.mConnectionPtr, statementPtr, i + 1, bool.booleanValue() ? 1 : 0);
                                break;
                            }
                    }
                } else {
                    nativeBindBlob(this.mConnectionPtr, statementPtr, i + 1, bool);
                }
            }
        }
    }

    private void throwIfStatementForbidden(PreparedStatement statement) {
        if (this.mOnlyAllowReadOnlyOperations && !statement.mReadOnly) {
            throw new SQLiteException("Cannot execute this statement because it might modify the database but the connection is read-only.");
        }
    }

    private static boolean isCacheable(int statementType) {
        if (statementType == 2 || statementType == 1) {
            return true;
        }
        return false;
    }

    private void applyBlockGuardPolicy(PreparedStatement statement) {
    }

    public void dump(Printer printer, boolean verbose) {
        dumpUnsafe(printer, verbose);
    }

    /* access modifiers changed from: package-private */
    public void dumpUnsafe(Printer printer, boolean verbose) {
        printer.println("Connection #" + this.mConnectionId + ":");
        if (verbose) {
            printer.println("  connectionPtr: 0x" + Long.toHexString(this.mConnectionPtr));
        }
        printer.println("  isPrimaryConnection: " + this.mIsPrimaryConnection);
        printer.println("  onlyAllowReadOnlyOperations: " + this.mOnlyAllowReadOnlyOperations);
        this.mRecentOperations.dump(printer, verbose);
        if (verbose) {
            this.mPreparedStatementCache.dump(printer);
        }
    }

    /* access modifiers changed from: package-private */
    public String describeCurrentOperationUnsafe() {
        return this.mRecentOperations.describeCurrentOperation();
    }

    /* access modifiers changed from: package-private */
    /* JADX WARNING: Code restructure failed: missing block: B:33:0x00e0, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:35:0x00e6, code lost:
        r12.close();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:36:0x00e9, code lost:
        throw r0;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:33:0x00e0 A[ExcHandler: all (r0v6 'th' java.lang.Throwable A[CUSTOM_DECLARE]), Splitter:B:6:0x003f] */
    @SuppressLint({"AvoidMethodInForLoops"})
    public void collectDbStats(ArrayList<SQLiteDebug.DbStats> dbStatsList) {
        ArrayList<SQLiteDebug.DbStats> arrayList = dbStatsList;
        int lookaside = nativeGetDbLookaside(this.mConnectionPtr);
        long pageCount = 0;
        long pageSize = 0;
        try {
            pageCount = executeForLong("PRAGMA page_count;", null, null);
            pageSize = executeForLong("PRAGMA page_size;", null, null);
        } catch (SQLiteException e) {
        }
        arrayList.add(getMainDbStatsUnsafe(lookaside, pageCount, pageSize));
        CursorWindow window = new CursorWindow("collectDbStats");
        CursorWindow window2 = window;
        try {
            executeForCursorWindow("PRAGMA database_list;", null, window, 0, 0, false, null);
            int i = 1;
            while (true) {
                int i2 = i;
                if (i2 >= window2.getNumRows()) {
                    break;
                }
                String name = window2.getString(i2, 1);
                String path = window2.getString(i2, 2);
                long pageCount2 = 0;
                long pageSize2 = 0;
                pageCount2 = executeForLong("PRAGMA " + name + ".page_count;", null, null);
                pageSize2 = executeForLong("PRAGMA " + name + ".page_size;", null, null);
                String label = "  (attached) " + name;
                if (!path.isEmpty()) {
                    label = label + ": " + path;
                }
                SQLiteDebug.DbStats dbStats = new SQLiteDebug.DbStats(label, pageCount2, pageSize2, 0, 0, 0, 0);
                arrayList.add(dbStats);
                i = i2 + 1;
            }
            window2.close();
        } catch (SQLiteException e2) {
        } catch (Throwable th) {
        }
    }

    /* access modifiers changed from: package-private */
    public void collectDbStatsUnsafe(ArrayList<SQLiteDebug.DbStats> dbStatsList) {
        dbStatsList.add(getMainDbStatsUnsafe(0, 0, 0));
    }

    private SQLiteDebug.DbStats getMainDbStatsUnsafe(int lookaside, long pageCount, long pageSize) {
        String label = this.mConfiguration.path;
        if (!this.mIsPrimaryConnection) {
            label = label + " (" + this.mConnectionId + ")";
        }
        SQLiteDebug.DbStats dbStats = new SQLiteDebug.DbStats(label, pageCount, pageSize, lookaside, this.mPreparedStatementCache.hitCount(), this.mPreparedStatementCache.missCount(), this.mPreparedStatementCache.size());
        return dbStats;
    }

    public String toString() {
        return "SQLiteConnection: " + this.mConfiguration.path + " (" + this.mConnectionId + ")";
    }

    private PreparedStatement obtainPreparedStatement(String sql, long statementPtr, int numParameters, int type, boolean readOnly) {
        PreparedStatement statement = this.mPreparedStatementPool;
        if (statement != null) {
            this.mPreparedStatementPool = statement.mPoolNext;
            statement.mPoolNext = null;
            statement.mInCache = false;
        } else {
            statement = new PreparedStatement();
        }
        statement.mSql = sql;
        statement.mStatementPtr = statementPtr;
        statement.mNumParameters = numParameters;
        statement.mType = type;
        statement.mReadOnly = readOnly;
        return statement;
    }

    private void recyclePreparedStatement(PreparedStatement statement) {
        statement.mSql = null;
        statement.mPoolNext = this.mPreparedStatementPool;
        this.mPreparedStatementPool = statement;
    }

    /* access modifiers changed from: private */
    public static String trimSqlForDisplay(String sql) {
        return sql.replaceAll("[\\s]*\\n+[\\s]*", " ");
    }
}

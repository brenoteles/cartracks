package de.dennisguse.opentracks.util;

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class AutoCloseableIterator<T> implements Iterator<T>, AutoCloseable {

    protected Cursor cursor;

    public AutoCloseableIterator(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    @NonNull
    public final T next() {
        if (cursor == null || !cursor.moveToNext()) {
            throw new NoSuchElementException();
        }
        return get();
    }

    public final int getPosition() {
        return cursor.getPosition();
    }

    public final boolean moveToFirst() {
        return cursor.moveToFirst();
    }

    public final boolean moveToPosition(int position) {
        return cursor.moveToPosition(position);
    }

    @Override
    public final boolean hasNext() {
        if (cursor == null) {
            return false;
        }
        return !cursor.isLast() && !cursor.isAfterLast();
    }

    @NonNull
    public abstract T get();

    @NonNull
    public final T get(int position) {
        moveToPosition(position);
        return get();
    }

    public final int getCount() {
        return cursor.getCount();
    }

    @Override
    public final void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void close() {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }
}

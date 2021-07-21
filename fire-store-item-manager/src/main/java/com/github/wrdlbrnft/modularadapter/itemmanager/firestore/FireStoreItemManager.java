package com.github.wrdlbrnft.modularadapter.itemmanager.firestore;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.github.wrdlbrnft.modularadapter.itemmanager.ItemManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 22/10/2017
 */

public class FireStoreItemManager<T> implements ItemManager<T> {

    private static final String TAG = "FireStoreItemManager";

    public interface ItemMapper<T> {
        T apply(DocumentSnapshot document);
    }

    public interface Filter<T> {
        boolean apply(T item);
    }

    public static class Builder<T> {

        private final AppCompatActivity mActivity;
        private final Query mQuery;

        private Filter<T> mFilter = item -> true;
        private ItemMapper<T> mItemMapper;

        public Builder(AppCompatActivity activity, Query query) {
            mActivity = activity;
            mQuery = query;
        }

        public Builder<T> setFilter(Filter<T> filter) {
            mFilter = filter;
            return this;
        }

        public Builder<T> setItemMapper(ItemMapper<T> mapper) {
            mItemMapper = mapper;
            return this;
        }

        public FireStoreItemManager<T> build() {
            return new FireStoreItemManager<>(mActivity, mQuery, mItemMapper, mFilter);
        }
    }

    private final ItemMapper<T> mMapper;
    private final List<ChangeSetCallback> mChangeSetCallbacks = new ArrayList<>();

    private List<DocumentSnapshot> mDocuments = new ArrayList<>();

    public FireStoreItemManager(AppCompatActivity activity, Query query, ItemMapper<T> mapper, Filter<T> filter) {
        mMapper = mapper;

        query.addSnapshotListener(activity, (documentSnapshots, e) -> {
            if (documentSnapshots == null) {
                Log.e(TAG, "There is a problem with the supplied query: " + query, e);
                return;
            }

            final List<DocumentSnapshot> currentDocuments = new ArrayList<>();
            for (DocumentSnapshot document : documentSnapshots.getDocuments()) {
                final T item = mapper.apply(document);
                if (filter.apply(item)) {
                    currentDocuments.add(document);
                }
            }

            mDocuments = currentDocuments;

            for (ChangeSetCallback changeSetCallback : mChangeSetCallbacks) {
                changeSetCallback.onChangeSetAvailable(adapter -> {
                    for (DocumentChange change : documentSnapshots.getDocumentChanges()) {
                        final T item = mapper.apply(change.getDocument());
                        if (filter.apply(item)) {
                            switch (change.getType()) {
                                case ADDED:
                                    adapter.notifyAdd(change.getNewIndex(), 1);
                                    break;
                                case MODIFIED:
                                    if (change.getNewIndex() == change.getOldIndex()) {
                                        adapter.notifyChange(change.getNewIndex(), 1);
                                    } else {
                                        adapter.notifyMove(change.getOldIndex(), change.getNewIndex());
                                    }
                                    break;
                                case REMOVED:
                                    adapter.notifyRemove(change.getOldIndex(), 1);
                                    break;
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public T getItem(int position) {
        final DocumentSnapshot document = mDocuments.get(position);
        return mMapper.apply(document);
    }

    @Override
    public int getItemCount() {
        if (mDocuments == null) {
            return 0;
        }
        return mDocuments.size();
    }

    @Override
    public void addChangeSetCallback(ChangeSetCallback callback) {
        mChangeSetCallbacks.add(callback);
    }

    @Override
    public void removeChangeSetCallback(ChangeSetCallback callback) {
        mChangeSetCallbacks.remove(callback);
    }

    @Override
    public void addStateCallback(StateCallback callback) {

    }

    @Override
    public void removeStateCallback(StateCallback callback) {

    }
}

package offlinesync;

import com.baasbox.android.BaasException;

import java.io.IOException;

import exceptions.SyncException;

/**
 * Created by Simone on 16/07/2014.
 */


    public interface ConflictPolicy <LOCAL_STORAGE,LOCAL_OBJECT>{

        public enum Action {
            DELETE_ON_CLIENT,
            DELETE_ON_SERVER,
            UPDATE_ON_CLIENT,
            UPDATE_ON_SERVER
        }

        public Action onConflict(LocalSyncInfo local, RemoteSyncInfo remote) throws IllegalArgumentException;

        public void executeSync(LOCAL_STORAGE localStorage, LOCAL_OBJECT local) throws SyncException;

        public  void executeSyncAll(LOCAL_STORAGE localStorage)throws IOException, SyncException;

    }


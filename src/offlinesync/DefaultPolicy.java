package offlinesync;

import android.net.LocalSocket;
import android.util.Log;

import com.baasbox.android.BaasBox;
import com.baasbox.android.BaasException;
import com.baasbox.android.BaasResult;
import com.baasbox.android.BaasUser;
import com.baasbox.android.json.JsonArray;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.net.HttpRequest;


import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import exceptions.SyncException;

/**
 * Created by Simone on 16/07/2014.
 */
public class DefaultPolicy implements ConflictPolicy<LocalStorage, JsonObject> {


    public DefaultPolicy() {
    }


    /** This method establishes the action that should be performed in the case of a conflict
     * between a local document (on the storage) and a remote document (on Server).
     *
     * Local and remote documents has to be represented as LocalSyncInfo and RemoteSyncInfo, respectively.
     * These two objects contains only the necessary fields (of their original objects) to manage any conflicts.
     *
     *
     * FLAG DIRTY: 0 = not dirty, 1 = dirty updated, 2 = dirty deleted (3 = dirty created)
     * 3 cases of conflict: AC (dirty=1) - CS server wins return 0 (delete on client)
     *                      CC (dirty=2) - AS client wins return 1 (delete on server)
     *                      AC (dirty=1) - AS server wins return 2 (update on client)
     *                      CC - CS no conflict return 0 (delete on client);
     *
     *@param local, remote
     *
     **/
    @Override
    public Action onConflict(LocalSyncInfo local, RemoteSyncInfo remote) {
        int dirty = local.getDirty();
        Long local_version = local.getVersion();
        Long remote_version = remote.getVersion();
        switch (dirty) {
            case 1:
                if (remote.getDeletion_date() != null)  //AC-CS
                    return Action.DELETE_ON_CLIENT;
                else if(!local_version.equals(remote_version)) // there is a conflict only if they differ in @version
                    return Action.UPDATE_ON_CLIENT; //AC-AS
                else return Action.UPDATE_ON_SERVER; // NO CONFLICT (only AC)
            case 2:
                if (remote.getDeletion_date() != null)  //CC - CS
                    return Action.DELETE_ON_CLIENT;
                else return Action.DELETE_ON_SERVER; // CC - AS or NO CONFLICT (only CC)
            default:
                throw new IllegalArgumentException("dirty flag of LocalSyncInfo is invalid for conflict resolution");
        }
    }


    /** This method manages the conflict between a local document (on the local storage) and a remote document (on Server).
     * It is called in the first step of the Synchronization algorithm (executeSyncAll(...)) in order to manage conflicts according to Default Policy.
     *
     * The local JsonObject given has to be complete of "local_data" and "data" fields
     * The remote JsonObject given has to be only the JsonObject related to "data" field of the JsonObject retrieved from Server
     *
     * @param localStorage, local, remote, server_datetime
     *
     */
    private void executeSync(LocalStorage localStorage, JsonObject local, JsonObject remote, String server_datetime) throws IllegalArgumentException, SyncException {
        String serverID = remote.getString("id");
        String localObjectServerID = local.getObject("data").getString("id");
        if (!serverID.equals(localObjectServerID))
            throw new IllegalArgumentException("local object and remote object aren't the same object");
        LocalSyncInfo localSyncInfo = localStorage.getLocalSyncInfo(localObjectServerID);
        RemoteSyncInfo remoteSyncInfo = localStorage.getRemoteSyncInfo(remote);
        Action onConflictResult = this.onConflict(localSyncInfo, remoteSyncInfo);
        switch (onConflictResult) {
            case DELETE_ON_CLIENT:
                localStorage.deleteDocument(local);
                break;
            case UPDATE_ON_CLIENT:
                localStorage.replaceFromServer(local, remote, server_datetime);
                break;
            case DELETE_ON_SERVER:
                /* according to Default Policy, this deletion on Server has to be always executed:
                 * also in the case of an update or deletion (of this document) occurred in the meanwhile on Server,
                 * the resulting conflict (CC vs AS or CC vs CS) will be won again by the client  */
                String collection = remote.getString("@class");
                BaasBox baasBox = BaasBox.getDefault();
                /* check if the document has already been deleted on Server */
                BaasResult<JsonObject> result_deleted = baasBox.restSync(HttpRequest.GET, "deleted/document?where=id='"+serverID+"'" , null, true);
                /* connection problems */
                if (result_deleted.isFailed())
                    throw new SyncException(result_deleted.error());
                /* if success and document already deleted*/
                if (result_deleted.isSuccess()) {
                    try {
                        JsonArray jsonArray = result_deleted.get().getArray("data");
                        if (jsonArray.size() != 0) {
                            /* delete from the storage */
                            localStorage.deleteDocument(local);
                            break;
                        }
                    } catch (BaasException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        throw new RuntimeException(new SyncException(e));
                    }
                }
                /* else try to delete */
                BaasResult<JsonObject> result = baasBox.restSync(HttpRequest.DELETE, "document/" + collection + "/" + serverID, null, true);
                /* if result is failed due to connection problems or something related to DELETE operation  */
                if (result.isFailed())
                    throw new SyncException(result.error());
                /* if success: delete from the storage  */
                if (result.isSuccess())
                    localStorage.deleteDocument(local);
                break;
            case UPDATE_ON_SERVER:
                /* this update is managed in the second part of the syncAll process*/
                break;
        }
    }

    /** This method executes the synchronization of a single local object.
     * It is always called only in the second step of the Synchronization algorithm (executeSyncAll(...))
     * in order to send local updates, deletions or creations to the Server and manages any possible conflicts
     * according to Default Policy.
     *
     * @param localStorage, local
     *
     */
    @Override
    public void executeSync(LocalStorage localStorage, JsonObject local) throws SyncException {
        String serverID = local.getObject("data").getString("id");
        String collection = local.getObject("data").getString("@class");
        BaasBox baasBox = BaasBox.getDefault();
        int dirty = local.getObject("local_data").getLong("_dirty").intValue();
        switch (dirty) {

            /* UPDATE: if update on server has success, local object last_sync_date, _update-date, _dirty and @version fields will be updated;
            *           otherwise, only in case of an update or deletion occurred on Server,
            *           executeSync(JsonObject local, JsonObject remote) method will be called to manage the conflict */
            case 1:
                BaasResult<JsonObject> PUT_result = baasBox.restSync(HttpRequest.PUT, "document/" + collection + "/" + serverID, local.getObject("data"), true);
                if (PUT_result.isSuccess()) {
                    try {
                        localStorage.setUpdateDate(serverID, PUT_result.get().getString("server_datetime"));
                        localStorage.setLastSyncDate(serverID, PUT_result.get().getString("server_datetime"));
                        localStorage.setDirty(serverID, 0L);
                        localStorage.setVersion(serverID, PUT_result.get().getObject("data").getLong("@version"));
                        break;
                    } catch (BaasException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        throw new RuntimeException(new SyncException(e));
                    }
                }
                /* if something went wrong (connection problems or any change, update or delete, on Server) */
                else {
                    /* check if an update on Server has occurred */
                    BaasResult<JsonObject> GET_result = baasBox.restSync(HttpRequest.GET, "document/" + collection + "/" + serverID, null, true);
                    /* connection problems */
                    if (GET_result.isFailed())
                        throw new SyncException(GET_result.error());
                    try {
                        JsonObject remoteDataObject = GET_result.get().getObject("data");
                        Long remote_version = remoteDataObject.getLong("@version");
                        Long local_version = local.getObject("data").getLong("@version");
                        /* check if an update on Server has occurred (different @version) */
                        if(remoteDataObject.size()!=0 && !local_version.equals(remote_version)){
                            executeSync(localStorage, local, remoteDataObject, GET_result.get().getString("server_datetime"));
                            break;
                        }
                    } catch (BaasException e) {
                      Log.d("error", ExceptionUtils.getStackTrace(e));
                      throw new RuntimeException(new SyncException(e));
                    }

                    /* check if a deletion on Server has occurred */
                    BaasResult<JsonObject> get_result_deleted = baasBox.restSync(HttpRequest.GET, "deleted/document?where=id='" + serverID + "'", null, true);
                    /* connection problems */
                    if (get_result_deleted.isFailed())
                        throw new SyncException(get_result_deleted.error());
                    try {
                        JsonArray jsonArray = get_result_deleted.get().getArray("data");
                        if (jsonArray.size() != 0) {
                            /* local document will be deleted, if a deletion on Server has occurred */
                            localStorage.deleteDocument(local);
                            break;
                        }
                    }catch (BaasException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        throw new RuntimeException(new SyncException(e));
                    }
                }
                break;

            /* DELETE: if deletion on Server has success (or the document has already been deleted on Server in the meanwhile), the document is also deleted from the storage  */
            case 2:
                /* check if the document has already been deleted */
                BaasResult<JsonObject> result_deleted = baasBox.restSync(HttpRequest.GET, "deleted/document?where=id='" + serverID + "'", null, true);
                /* connection problems */
                if (result_deleted.isFailed())
                    throw new SyncException(result_deleted.error());
                /* if success and document has already been deleted*/
                if (result_deleted.isSuccess()) {
                    try {
                        JsonArray jsonArray = result_deleted.get().getArray("data");
                        if (jsonArray.size() != 0) {
                            localStorage.deleteDocument(local);
                            break;
                        }
                    } catch (BaasException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        throw new RuntimeException(new SyncException(e));
                    }
                }
                /* otherwise try to delete */
                BaasResult<JsonObject> DELETE_result = baasBox.restSync(HttpRequest.DELETE, "document/" + collection + "/" + serverID, null, true);
                /* if result is failed due to connection problems or something related to DELETE operation  */
                if (DELETE_result.isFailed())
                    throw new SyncException(DELETE_result.error());
                /* if success: delete from the storage */
                if (DELETE_result.isSuccess())
                    localStorage.deleteDocument(local);
                break;

            /* CREATE  */
            case 3:
                JsonObject dataObject = local.getObject("data");
                BaasResult<JsonObject> POST_Result = baasBox.restSync(HttpRequest.POST, "document/" + collection, dataObject, true);
                if (POST_Result.isFailed())
                    throw new SyncException(POST_Result.error());
                try {
                    localStorage.updateAfterCreation(local, POST_Result.get().getObject("data"));
                } catch (BaasException e) {
                    Log.d("error", ExceptionUtils.getStackTrace(e));
                    throw new RuntimeException(new SyncException(e));
                }
                break;
            default:
                throw new IllegalArgumentException("The given JsonObject has not a valid _dirty value");
        }

    }



    /** SYNC PROCESS:  Synchronization of the whole local storage
     * The lastSyncDate used in the query to the Server (_update_date > lastSyncDate and _delete_date > lastSyncDate)
     * is exactly the starting date of the previous sync (converted in HashCode format for security reasons)
     *
     * 1° step: conflicts management of the updates and deletions occurred on Server since the previous sync
     * 2° step: synchronization of the remaining dirty documents in the storage
     *
     * @param localStorage
     * **/
    @Override
    public void executeSyncAll(LocalStorage localStorage) throws IOException, SyncException {
        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> getTime = baasBox.restSync(HttpRequest.GET, "refresh", null, true);
        String thisSyncDate = null;
        try {
            thisSyncDate = getTime.get().getString("server_datetime");
        } catch (BaasException e) {
            throw new RuntimeException(e);
        }
        if(thisSyncDate == null){
            throw new SyncException("can't obtain server_datetime");
        }
        String listOfIDs = localStorage.getStringOfIDsForSync();
        String stringOfCollections = localStorage.getStringOfCollectionsForSync();
        List<String> listOfCollections = localStorage.getAllCollectionsOnTheStorage();
        if (localStorage.getRootDirectory().listFiles().length == 0 || listOfCollections.size() == 0)
            throw new IllegalArgumentException("Empty Storage, nothing to sync!!!");
        String lastSyncDate = localStorage.getSyncDate();
        /** First step of sync process: conflicts management of the updates and deletions occurred on Server since the previous sync **/
        /* generate the HashCode of the current user Session Token for security reasons*/
        String sessionToken = sha256(BaasUser.current().getToken());
        String listOfIdAndDateEncoded = URLEncoder.encode("id in " + listOfIDs + " and _update_date > date('" + lastSyncDate + "') and (_audit.modifiedByToken<>'" + sessionToken + "' or _audit.modifiedByToken is null)", "utf-8");
        /* conflicts management of the updates occurred on Server */
        for (String currentCollection : listOfCollections) {
            BaasResult<JsonObject> GET_result_1 = baasBox.restSync(HttpRequest.GET, "document/" + currentCollection + "?where=" + listOfIdAndDateEncoded, null, true);
                /* connection problems */
            if (GET_result_1.isFailed())
                throw new SyncException(GET_result_1.error());
            try {
                JsonArray jsonArray = GET_result_1.get().getArray("data");
                List<JsonObject> listForFirstSyncStep = localStorage.getListForFirstSyncStep();
                if (jsonArray.size() != 0 && listForFirstSyncStep.size() != 0) {
                    for (JsonObject localJson : listForFirstSyncStep) {
                        for (Object aJsonArray : jsonArray) {
                            JsonObject j = (JsonObject) aJsonArray;
                                /* if they are the same object */
                            if (localJson.getObject("data").getString("id").equals(j.getString("id"))) {
                                    /* if localJson is dirty */
                                if (localJson.getObject("local_data").getLong("_dirty") != 0) {
                                    executeSync(localStorage, localJson, j, GET_result_1.get().getString("server_datetime"));
                                }
                                    /* otherwise the local object is replaced with that from Server */
                                else
                                    localStorage.replaceFromServer(localJson, j, GET_result_1.get().getString("server_datetime"));
                            }
                        }
                    }
                }
            } catch (BaasException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                throw new RuntimeException(new SyncException(e));
            }
        }

        /* refresh listOfIDs (in order to not consider any deletions occurred in the previous step)*/
        listOfIDs = localStorage.getStringOfIDsForSync();
        /* conflicts management of the deletions occurred on Server */
        String listOfIdAndDateEncodedForDeletedGet = URLEncoder.encode("id in " + listOfIDs + " and _delete_date > date('" + lastSyncDate + "') and _clazz in " + stringOfCollections + " and (_audit.modifiedByToken<>'" + sessionToken + "' or _audit.modifiedByToken is null)", "utf-8");
        BaasResult<JsonObject> GET_result_deleted = baasBox.restSync(HttpRequest.GET, "deleted/document?where=" + listOfIdAndDateEncodedForDeletedGet, null, true);
        if (GET_result_deleted.isFailed())
            throw new SyncException(GET_result_deleted.error());
        try {
            JsonArray jsonArrayDeleted = GET_result_deleted.get().getArray("data");
            List<JsonObject> listForFirstSyncStep = localStorage.getListForFirstSyncStep();
            if (jsonArrayDeleted.size() != 0 && listForFirstSyncStep.size() != 0) {
                for (JsonObject localJson : listForFirstSyncStep) {
                    for (Object aJsonArray : jsonArrayDeleted) {
                        JsonObject j = (JsonObject) aJsonArray;
                        /* if they are the same object */
                        if (localJson.getObject("data").getString("id").equals(j.getString("id"))) {
                            /* if localJson is dirty */
                            if (localJson.getObject("local_data").getLong("_dirty") != 0) {
                                executeSync(localStorage, localJson, j, GET_result_deleted.get().getString("server_datetime"));
                            }
                            /* otherwise local object is deleted from local storage */
                            else localStorage.deleteDocument(localJson);
                        }
                    }
                }
            }
        } catch (BaasException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(new SyncException(e));
        }


        /** Second step of sync process: synchronization of the remaining dirty documents in the storage **/
        List<JsonObject> listOfDirtyDocuments = localStorage.getDirtyDocuments();
        if (listOfDirtyDocuments.size() != 0) {
            for (JsonObject currentLocalDirtyObject : listOfDirtyDocuments) {
                executeSync(localStorage, currentLocalDirtyObject);
            }
        }
        localStorage.saveSyncDate(thisSyncDate);
    }



    /** This method executes a GET operation on Server in order to obtain any NEW documents created in the given collection on Server.
     *  It returns a NewDocumentsInfo object containing a list of the new documents and the "server_datetime" field
     *  of the GET response from Server (that is necessary if the User wants to save one of these documents on the storage)
     *
     * @param localStorage, collection
     */
    public NewDocumentsInfo fetchNewDocuments(LocalStorage localStorage, String collection) {
        List<JsonObject> newDocuments = new ArrayList<JsonObject>();
        BaasBox baasBox = BaasBox.getDefault();
        String serverDateTime = null;
        String listOfNewDocuments = null;
        String lastSyncDate = localStorage.getSyncDate();

        /* if there isn't a previous sync (lastSyncDate == null), the GET operation returns all the documents in the given collection */
        if (lastSyncDate == null) {
            BaasResult<JsonObject> GET_result_1 = baasBox.restSync(HttpRequest.GET, "document/" + collection, null, true);
            if (GET_result_1.isFailed())
                throw new RuntimeException(GET_result_1.error());
            try {
                JsonArray jsonArray = GET_result_1.get().getArray("data");
                if (jsonArray.size() != 0) {
                    serverDateTime = GET_result_1.get().getString("server_datetime");
                    for (Object aJsonArray : jsonArray) {
                        JsonObject j = (JsonObject) aJsonArray;
                        String idServer = j.getString("id");
                        JsonObject jsonObject = localStorage._getByIDWithoutCollection(idServer);
                        /* if JsonObject already exists in the storage, it won't be added to the list */
                        if (jsonObject == null)
                            newDocuments.add(j);
                    }
                }
            } catch (BaasException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                throw new RuntimeException(e);
            }
        }
        /* otherwise the GET operation returns all the documents in the given collection with _creation_date field > lastSyncDate */
        else {

            String sessionToken = sha256(BaasUser.current().getToken());
            try {
                listOfNewDocuments = URLEncoder.encode("_creation_date > date('" + lastSyncDate + "') and (_audit.modifiedByToken<>'"+sessionToken+"' or _audit.modifiedByToken is null)", "utf-8");
            } catch (UnsupportedEncodingException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
            }
            if (listOfNewDocuments != null) {
                BaasResult<JsonObject> GET_result_2 = baasBox.restSync(HttpRequest.GET, "document/" + collection + "?where=" + listOfNewDocuments, null, true);
                if (GET_result_2.isFailed())
                    throw new RuntimeException(GET_result_2.error());
                try {
                    JsonArray jsonArray_1 = GET_result_2.get().getArray("data");
                    if (jsonArray_1.size() != 0) {
                        serverDateTime = GET_result_2.get().getString("server_datetime");
                        for (Object aJsonArray : jsonArray_1) {
                            JsonObject j = (JsonObject) aJsonArray;
                            String idServer_1 = j.getString("id");
                            JsonObject jsonObject_1 = localStorage._getByIDWithoutCollection(idServer_1);
                            /* if JsonObject already exists in the storage, it won't be added to the list */
                            if (jsonObject_1 == null)
                                newDocuments.add(j);
                        }
                    }
                } catch (BaasException e) {
                    Log.d("error", ExceptionUtils.getStackTrace(e));
                    throw new RuntimeException(e);
                }
            }
        }
        return new NewDocumentsInfo(newDocuments, serverDateTime);
    }

    /**
     * this class represents an object made up of a list of JsonObjects and a String
     */
    public class NewDocumentsInfo {
        private List<JsonObject> list;
        private String serverDateTime;

        public NewDocumentsInfo(List<JsonObject> list, String serverDateTime) {
            this.list = list;
            this.serverDateTime = serverDateTime;
        }

        public List<JsonObject> getList() {
            return list;
        }

        public void setList(List<JsonObject> list) {
            this.list = list;
        }

        public String getServerDateTime() {
            return serverDateTime;
        }

        public void setServerDateTime(String serverDateTime) {
            this.serverDateTime = serverDateTime;
        }
    }

    /**
     * This method generates the HashCode of the given String
     *
     * @param base
     */
    public static String sha256(String base) {
        if (base==null) return null;
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

